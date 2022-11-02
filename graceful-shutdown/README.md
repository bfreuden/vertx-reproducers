
Start the server:
```bash
mvn compile exec:java
```

Test the server with a request returning immediately:
```bash
curl localhost:9000
```

Call the server with 10 and 9 seconds-long requests (using vert.x timers):
```bash
curl localhost:9000?delay=10
```
```bash
curl localhost:9000?delay=9
```

CTRL-C the server.

Then call the server again, you get a 503:
```bash
curl localhost:9000
```

The 2 long requests should run successfully.
