[![Build Status](http://api.screwdriver.corp.yahoo.com:4080/badge/78468/component/icon)](http://api.screwdriver.corp.yahoo.com:4080/badge/78468/component/target)
[![Build Status](http://api.screwdriver.corp.yahoo.com:4080/badge/78468/release/icon)](http://api.screwdriver.corp.yahoo.com:4080/badge/78468/release/target)

Viper is a utility that returns a live host from a list of host names.
Viper will continuously monitor each host name and report when any hosts become unresponsive.
Several policies are available to pick one live host from the list:

* first live: returns the first live host from the ordered list of hosts
* round robin: each request returns the next live host from the list.
* random: randomly returns one of the available live hosts

## Usage

```
import com.yahoo.viper.*;
...

// Create list of hosts to monitor
List<HostInfo> hosts = Arrays.asList(
        new HostInfo("yahoo.com", 80),
        new HostInfo("http://google.com"),
        new HostInfo("mysql.db", 3365));

// Ping the hosts every 5 seconds and return live hosts in round robin fashion
int checkPeriodMs = 5000;
int retries = 0;
HostMonitor hmonitor = new HostMonitor("Name", LoadBalancingPolicy.ROUND_ROBIN, checkPeriodMs, retries);

// Get a live host
HostInfo host = hmonitor.liveHost();
```

## Logging

The logging output has been carefully crafted to provide useful information with as little noise as possible.

* The only time an ERROR is output is when there are no live hosts. You should be able to set an alert for ERROR messages.
* A WARN message is output when at least one host is unavailable.
* When things are running normally, a status message is output about once a minute.
* Full stack traces due to failed checks are suppressed. This flag can be overridden for debugging purposes.
* Similar error messages are suppressed and summarized once a minute so that frequest failed checks do not pollute the log.

## The server Tool

The server tool is used to simulate server conditions in order to test
the monitoring library. Start the tool using the following command.

```
$ bin/server
Commands:
  <port> up - handle requests to the port
  <port> down - stop handling requests to the port
  <port> hang - hang requests to the port
  <port> error - fail requests to the port
```

To create two servers listenting on port 2000 and 2001, type
```
2000 up
2001 up
```

To cause one to hang and another to error, type
```
2000 hang
2000 error
```

To restore one of the servers, type
```
2000 up
```

## The monitor Tool

The monitor tool is a convenient way to test the monitoring library on
one or more servers. It can be used on the simulated servers created
by the server tool or on real server instances (or both).

The monitor tool accepts three types of host specifications:

* port - refers to the specified port on localhost. e.g. 2000
* host:port - refers to the specified port on the specified host. e.g. xyzdb:2001
* url - refers to the specified URL. http://yahoo.com/sports

Here's an example of using the tool to monitor two hosts. The output is annotated with comments, which
are prefixed with #.

```
$ bin/monitor http://localhost:2000 2001

# All is well. This message is printed at least once a minute so you know the library is running fine
2016-02-12 23:49:25,256 [INFO] All hosts are up. (period=500ms). (120 checks)

# The monitor tool fetches a live host and prints what it fetched
2016-02-12 23:49:26,635 [INFO] monitor: host localhost/127.0.0.1:2001 is live

# Notice that the next live host fetched is the other host in the list
2016-02-12 23:49:36,635 [INFO] monitor: host http://localhost:2000 is live

# In the server tool, the command "2000 hung" was issued. The following status will be printed
# at least once a minute while the situation remains
2016-02-12 23:49:41,840 [WARN] 1 out of 2 hosts are not live: http://localhost:2000(hung)

# The monitor tool registered for status updates from the library. This is the notification
# message (along with other information as well; see docs).
2016-02-12 23:49:41,840 [WARN] monitor: 1 out of 2 hosts are not live: http://localhost:2000(hung)
2016-02-12 23:49:46,640 [INFO] monitor: host localhost/127.0.0.1:2001 is live
2016-02-12 23:49:56,641 [INFO] monitor: host localhost/127.0.0.1:2001 is live
2016-02-12 23:50:01,949 [INFO] localhost/127.0.0.1:2001: Connection refused

# In the server tool, the command "2001 down" was issued. Notice that the logging level is ERROR
# because all hosts are unavailable.
2016-02-12 23:50:02,453 [ERROR] All 2 hosts are not live: http://localhost:2000(hung) localhost/127.0.0.1:2001
2016-02-12 23:50:02,453 [ERROR] monitor: All 2 hosts are not live: http://localhost:2000(hung) localhost/127.0.0.1:2001
2016-02-12 23:50:06,644 [INFO] monitor: no live hosts
2016-02-12 23:50:16,649 [INFO] monitor: no live hosts

# This is the error being emitted by localhost:2001.  Notice that if the errors are suppressed
# if they are the same, to avoid polluting the logs. The number of suppressions is printed.
2016-02-12 23:50:20,563 [INFO] localhost/127.0.0.1:2001: Connection refused [logged 36 times]

# In the server tool, the command "2001 up" was issued
2016-02-12 23:50:20,563 [INFO] Thread[Checker-9,5,main] localhost/127.0.0.1:2001: is now live
2016-02-12 23:50:21,065 [WARN] 1 out of 2 hosts are not live: http://localhost:2000(hung)
2016-02-12 23:50:21,066 [WARN] monitor: 1 out of 2 hosts are not live: http://localhost:2000(hung)
2016-02-12 23:50:26,652 [INFO] monitor: host localhost/127.0.0.1:2001 is live
2016-02-12 23:50:32,498 [INFO] http://localhost:2000: Unexpected end of file from server

# In the server tool, the command "2000 up" was issued
2016-02-12 23:50:32,644 [INFO] Thread[Checker-9,5,main] http://localhost:2000: is now live

# All is well again
2016-02-12 23:50:33,146 [INFO] All hosts are up. (period=500ms). (24 checks)
2016-02-12 23:50:33,147 [INFO] monitor: All hosts are up. (period=500ms)
2016-02-12 23:50:36,655 [INFO] monitor: host http://localhost:2000 is live
2016-02-12 23:50:46,655 [INFO] monitor: host localhost/127.0.0.1:2001 is live
```
