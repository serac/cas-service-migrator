CAS 3.x Service Registry Migrator
======

This is a simple Spring-based utility to convert registered service entries in
a CAS 3.x database to JSON files with the following structure:

    {
      "name": "Friendly Service Name",
      "id": 123456790,
      "description": "Optional long service description",
      "enabled": true,
      "anonymousAccess": false,
      "ignoreAttributes": false,
      "allowedAttributes": [
        "uid",
        "sn",
        "givenName",
        "displayName"
      ],
      "allowedToProxy": false,
      "serviceId": "https://www.example.com/**",
      "theme": null,
      "ssoEnabled": true,
      "evaluationOrder": 600,
      "usernameAttribute": null
    }

The ID field is based on the current system time at nanosecond resolution, and
the output names are of the following form:

    org.jasig.cas.services.RegisteredServiceImpl-$id.json

Usage Example
------

Use the database.properties.sample file to create a database.properties in the
project root directory with connection settings appropriate for your CAS 3.x
service registry. Then execute the following command:

    ./migrate.sh /path/to/output/directory

where /path/to/output/directory is replaced by a real directory where you want
JSON output written.