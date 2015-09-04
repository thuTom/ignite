Ignite Web Agent
======================================
Ignite Web Agent is a java standalone application that allow to connect Ignite Grid to Ignite Web Control Center.
Ignite Web Agent communicates with grid nodes via REST interface and connects to Ignite Web Control Center via web-socket.

Two main functions of Ignite Web Agent:
 1. Proxy between Ignite Web Control Center and Ignite Grid to execute SQL statements and collect metrics for monitoring.
    You may need to specify URI for connect to Ignite REST server via "-n" option.

 2. Proxy between Ignite Web Control Center and user RDBMS to collect database metadata for later CacheTypeMetadata configuration.
    You may need to copy JDBC driver into "./jdbc-drivers" subfolder or specify path via "-drv" option.

Usage example:
    ignite-control-center-agent.sh -l john.smith@gmail.com -p qwerty -s wss://control-center.example.com

Test drive of Ignite Web Agent:
    In order to simplify evaluation two test drive modes were implemented:

    1) Test drive for metadata load from database. Activated by option: -tm or --test-drive-metadata.
       In this mode an in-memory H2 database will started and could be acessed via JDBC URL: jdbc:h2:mem:test-drive-db.
       How to evaluate: go to Ignite Web Control Center "Metadata" screen, select "Load from database" and enter JDBC URL.
       You should see list of available schemas and tables. Select some of them and click "Save".

    2) Test drive for SQL. Activated by option: -ts or --test-drive-sql.
       In this mode internal Ignite node will be started. Cache created and populated with data.
       How to evaluate: go to Ignite Web Control Center "SQL" menu, create new notebook.
       In notebook paragraph enter SQL queries for tables: "Country, Department, Employee" in "test-drive-employee" cache
        and for tables: "Parking, Car" in "test-drive-car" cache.

       For example:
        2.1) select "test-drive-car" cache,
        2.2) enter SQL:
                select count(*) cnt, p.ParkingName from car c
                 inner join PARKING p on (p.PARKINGID=c.PARKINGID)
                group by c.PARKINGID order by p.ParkingName
        2.3) Click "Execute" button. You should get some data in table. Click charts buttons to see auto generated charts.

Configuration file:
    Should be a file with simple line-oriented format as described here: http://docs.oracle.com/javase/7/docs/api/java/util/Properties.html#load(java.io.Reader)

    Available entries names:
        login
        password
        serverURI
        nodeURI
        driverFolder
        test-drive-metadata
        test-drive-sql

    Example configuration file:
        login=john.smith@gmail.com
        serverURI=wss://control-center.example.com:3001
        test-drive-sql=true

Options:
    -h, --help
       Print this help message.

    -c, --config
       Path to optional configuration file.

    -drv, --driver-folder
       Path to folder with JDBC drivers, for example "/home/user/drivers".
       Default: "./jdbc-drivers".

    -l, --login
       User's login (email) on Ignite Web Agent.

    -n, --node-uri
       URI for connect to Ignite REST server, for example: "http://localhost:8080".
       Default: "http://localhost:8080".

    -p, --password
       User's password.

    -s, --server-uri
       URI for connect to Ignite Web Agent, for example: "wss://control-center.example.com:3001".
       Default: "wss://localhost:3001".

    -tm, --test-drive-metadata
       Start H2 database with sample tables in same process.
       JDBC URL for connect to sample database: jdbc:h2:mem:test-drive-db

    -ts, --test-drive-sql
       Create cache and populate it with sample data for use in query.


Ignite Web Agent Build Instructions
==============================================
If you want to build from sources run following command in Ignite project root folder:
    mvn clean package -pl :ignite-control-center-agent -am -P control-center -DskipTests=true
