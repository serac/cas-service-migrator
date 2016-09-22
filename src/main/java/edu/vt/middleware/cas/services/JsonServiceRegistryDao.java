/*
  $Id$

  Copyright (C) 2013 Virginia Tech.
  All rights reserved.

  SEE LICENSE FOR MORE INFORMATION

  Author:  Middleware
  Email:   middleware@vt.edu
  Version: $Revision$
  Updated: $Date$
*/
package edu.vt.middleware.cas.services;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jasig.cas.services.AbstractRegisteredService;
import org.jasig.cas.services.RegisteredService;
import org.jasig.cas.services.ServiceRegistryDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

/**
 * JSON implementation of {@link ServiceRegistryDao} that writes each registered service entry to a JSON file
 * on the filesystem. Changes are synchronized between filesystem entries and the entries managed by this component,
 * but filesystem data is treated with authority.
 *
 * @author  Marvin S. Addison
 * @version  $Revision$
 */
public class JsonServiceRegistryDao implements ServiceRegistryDao {

    /**
     * File extension of registered service JSON files.
     */
    private static final String FILE_EXTENSION = ".json";

    /**
     * Filter that selects for files ending with FILE_EXTENSION.
     */
    private static final FilenameFilter FILENAME_FILTER = new FilenameFilter() {
        public boolean accept(final File file, final String s) {
            return s.endsWith(FILE_EXTENSION);
        }
    };

    /**
     * Logger instance.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonServiceRegistryDao.class);

    /**
     * Map of service ID to registered service.
     */
    private ConcurrentMap<Long, RegisteredService> serviceMap = new ConcurrentHashMap<Long, RegisteredService>();

    /**
     * Jackson object mapper.
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Path to directory containing JSON file data.
     */
    @NotNull
    private File serviceRegistryDirectory;


    /**
     * Sets the path to the directory where JSON service registry entries are
     * stored.
     *
     * @param directory Absolute path to directory.
     */
    public void setServiceRegistryDirectory(final String directory) {
        this.serviceRegistryDirectory = new File(directory);
        Assert.isTrue(this.serviceRegistryDirectory.exists(), serviceRegistryDirectory + " does not exist");
        Assert.isTrue(this.serviceRegistryDirectory.isDirectory(), serviceRegistryDirectory + " is not a directory");
    }


    /**
     * {@inheritDoc}
     */
    public synchronized RegisteredService save(final RegisteredService service) {
        if (service.getId() < 0 && service instanceof AbstractRegisteredService) {
            ((AbstractRegisteredService) service).setId(System.nanoTime());
        }
        LockedOutputStream out = null;
        try {
            out = new LockedOutputStream(new FileOutputStream(makeFile(service)));
            objectMapper.writeValue(out, service);
        } catch (IOException e) {
            throw new RuntimeException("IO error opening file stream.", e);
        } finally {
            closeStream(out);
        }
        load();
        return findServiceById(service.getId());
    }


    /**
     * {@inheritDoc}
     */
    public synchronized boolean delete(final RegisteredService service) {
        serviceMap.remove(service.getId());
        final boolean result = makeFile(service).delete();
        load();
        return result;
    }


    /**
     * {@inheritDoc}
     */
    public synchronized List<RegisteredService> load() {
        final ConcurrentMap<Long, RegisteredService> temp = new ConcurrentHashMap<Long, RegisteredService>();
        RegisteredService service;
        int errorCount = 0;
        for (File file : this.serviceRegistryDirectory.listFiles(FILENAME_FILTER)) {
            BufferedInputStream in = null;
            try {
                in = new BufferedInputStream(new FileInputStream(file));
                service = objectMapper.readValue(in, getRegisteredServiceType(file));
                temp.put(service.getId(), service);
            } catch (Exception e) {
                errorCount++;
                LOGGER.error("Error reading {}", file, e);
            } finally {
                closeStream(in);
            }
        }
        if (errorCount == 0) {
            this.serviceMap = temp;
        }
        return new ArrayList<RegisteredService>(this.serviceMap.values());
    }


    /**
     * {@inheritDoc}
     */
    public RegisteredService findServiceById(final long id) {
        return serviceMap.get(id);
    }


    /**
     * Creates a JSON file for a registered service.
     *
     * @param  service  Registered service.
     *
     * @return  JSON file in service registry directory.
     */
    protected File makeFile(final RegisteredService service) {
        return new File(
                serviceRegistryDirectory,
                service.getClass().getName() + "-" + service.getId() + FILE_EXTENSION);
    }


    /**
     * Gets the type of registered service from the file name of the given file.
     * The naming convention follows that established in {@link #makeFile(org.jasig.cas.services.RegisteredService)}.
     *
     * @param  file  JSON registered service file.
     *
     * @return  Class name extracted from file name.
     */
    @SuppressWarnings("unchecked")
    private Class<? extends RegisteredService> getRegisteredServiceType(final File file) {
        final String className = file.getName().substring(0, file.getName().indexOf('-'));
        try {
            return (Class<? extends RegisteredService>) Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Cannot create instance of " + className);
        }
    }


    /**
     * Closes an input stream and swallows all IO errors.
     *
     * @param  in  Input stream.
     */
    private static void closeStream(final InputStream in) {
        if (in != null) {
            try {
                in.close();
            } catch (IOException e) {
                LOGGER.debug("Error closing input stream: {}", e);
            }
        }
    }


    /**
     * Closes an output stream and swallows all IO errors.
     *
     * @param  out  Output stream.
     */
    private static void closeStream(final OutputStream out) {
        if (out != null) {
            try {
                out.close();
            } catch (IOException e) {
                LOGGER.debug("Error closing output stream: {}", e);
            }
        }
    }


    /**
     * Buffered output stream around a file that is exclusively locked for the
     * lifetime of the stream.
     */
    static class LockedOutputStream extends BufferedOutputStream {

        /** Lock held on file underneath stream. */
        private final FileLock lock;

        /** Flag to indicate underlying stream is already closed. */
        private boolean closed;

        /**
         * Creates a new instance by obtaining a lock on the underlying stream
         * that is held until the stream is closed.
         *
         * @param out Output stream.
         * @throws IOException If a lock cannot be obtained on the file.
         */
        public LockedOutputStream(final FileOutputStream out) throws IOException {
            super(out);
            this.lock = out.getChannel().lock();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            try {
                lock.release();
            } finally {
                closed = true;
                super.close();
            }
        }
    }
}
