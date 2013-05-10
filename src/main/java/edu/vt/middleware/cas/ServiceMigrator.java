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
package edu.vt.middleware.cas;

import java.io.File;
import java.io.FileNotFoundException;

import edu.vt.middleware.cas.services.JsonServiceRegistryDao;
import org.jasig.cas.services.AbstractRegisteredService;
import org.jasig.cas.services.JpaServiceRegistryDaoImpl;
import org.jasig.cas.services.RegisteredService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggerFactoryBinder;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Command line utility to migrate registered services from database to JSON files.
 *
 * @author Marvin S. Addison
 */
public class ServiceMigrator {

    private static ApplicationContext context;

    private static Logger logger = LoggerFactory.getLogger(ServiceMigrator.class);

    private final JsonServiceRegistryDao jsonServiceRegistryDao;

    private final JpaServiceRegistryDaoImpl jpaServiceRegistryDao;

    public static void main(final String[] args) {
        if (args.length < 1) {
            usage();
        }
        try {
            context = new ClassPathXmlApplicationContext("/applicationContext.xml");
        } catch (BeanInitializationException e) {
            if (e.getCause() instanceof FileNotFoundException) {
                System.out.println("Missing required file: " + e.getCause().getMessage());
            } else {
                e.printStackTrace();
            }
            usage();
        }
        if (!new File(args[0]).isDirectory()) {
            System.out.println("Invalid output directory " + args[0]);
            usage();
        }
        final JsonServiceRegistryDao json = context.getBean(JsonServiceRegistryDao.class);
        final JpaServiceRegistryDaoImpl jpa = context.getBean(JpaServiceRegistryDaoImpl.class);
        json.setServiceRegistryDirectory(args[0]);
        final int count = new ServiceMigrator(json, jpa).migrate();
        System.out.println("Migrated " + count + " services");
    }

    public ServiceMigrator(final JsonServiceRegistryDao jsonDao, final JpaServiceRegistryDaoImpl jpaDao) {
        this.jsonServiceRegistryDao = jsonDao;
        this.jpaServiceRegistryDao = jpaDao;
    }

    public int migrate() {
        int count = 0;
        for (RegisteredService service : jpaServiceRegistryDao.load()) {
            if (service instanceof AbstractRegisteredService) {
                ((AbstractRegisteredService) service).setId(System.nanoTime());
            }
            logger.info("Migrating {}", service);
            jsonServiceRegistryDao.save(service);
            count++;
        }
        return count;
    }

    private static void usage() {
        System.out.println("USAGE: ServiceMigrator /path/to/output/directory");
        System.out.println("       A database.properties file must be located in current working directory.");
        System.exit(0);
    }
}
