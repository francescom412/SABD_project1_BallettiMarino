# SABD 2019/2020 first project
Authors: Marco Balletti, Francesco Marino

## Docker container descritption and initialization notes:

### Containers starting and stopping scripts:
* `start-docekrs.sh` creates a Docker network and starts every container,
* `stop-dockers.sh` stops and removes every container and deletes the network.

### First start notes:
* **/etc/hosts/**:

	1. 	insert `127.0.0.1	hbase`
	2. insert `127.0.0.1	influxdb`

* **NiFi**: 

	1. connect to _http://localhost:9880/nifi/_,
	2. press on "*upload template*" under the "*Operate*" box,
	3. select `SABD_project1_dataset_import.xml` file inside the `nifi_template` directory.

* **Grafana**:
	1. connect to _http://localhost:3000/_,
	2. select the "*+*" tab,
	3. choose "*import*" option,
	4. select all of queries dashboard inside the `grafana-dashboards/grafana-JSON` directory.

### Containers descriptions:
* **NiFi** container:

	Used to import datasets from github to HDFS.
	`nifi_conf` and `nifi_state` directories are bounded to the container to implement local host machine persistence.
	
	`nifi-hdfs` files are automatically uploaded to the container and used to allow NiFi and HDFS communication.
	
* **HDFS** containers:
	
	Four containers working one as Namenode and three as Datanodes for the Hadoop Distributed Filesystem.
	Script inside the `hdfs-script` directory are automatically uploaded to the container and executed to format and start the distributed filesystem and create directories to store input and output files.
	
*  **HBase** container:

	For HDFS output file export to NoSQL datastore feature.
	
* **Grafana** container:

	For queries results graphical representation. The `grafana_storage` folder is automatically bounded to the container to implement local host machine persistence.
	
* **InfluxDB** container:

	For queries result export from HDFS to Grafana. The `influx_storage` directory is automatically bounded to the container to implement local host machine persistence of the time series database.