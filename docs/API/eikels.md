# Intro

Appelflap can store and serve large objects via HTTP. This can be useful for working around quota and object size limits in the browser's storage facilities.
This local fileserver is called Eekhoorn (pronounced like "Acorn"), which is Dutch for "squirrel", animals known to stash acorns. Perhaps coincidentally, "Eekhoorn" in Dutch sounds exactly like "Acorn" in English, but "acorn" is "eikel" in Dutch.

# Preliminaries
1. The identity of a resource is its (URL path, URL query string).
2. Authentication is required for operations that modify state. That means that anyone who can access the server has read access to the resources.
3. If you want to set any headers beyond the bare minimum (say Content-Type) that the content should be served out with, you can specify those when you PUT the resource.
4. `DELETE`s and `PUT`s don't step on the toes of ongoing `GET`s or other `PUT`s. `PUT`s don't become `GET`able unless they succeeded. If you squint it's basically ACID. Perhaps we should call it ACId (small D) because there's no way to fsync() a directory using the NIO API's available to Android 7, hence durability could suffer if the OS crashes.

You can use it right from your PWA (running in Appelflap, [see README.md](README.md)) to PUT loads of stuff unhampered by browser storage quotas, and then you can retrieve it using GET it or pass local webserver URLs to your Javascript APIs (say, some video player).

There's a simple management API available too.

It's supposed to use Java APIs available on the Android 7 JRE and up, which is why we're using Jetty 9.3 as newer versions require NIO support which the Android 7 JRE lacks.

## Minitutorial

This will run Eekhoorn standalone (not embedded in an Android app).

### Build

```
$ cd eekhoorn
$ gradle -b ./build.gradle --no-build-cache --configure-on-demand --console rich --no-daemon fatjar

BUILD SUCCESSFUL in 7s
2 actionable tasks: 2 executed
<-------------> 0% WAITING
> IDLE
```

### Run the server

#### Parameters
```
$ java -jar build/libs/eekhoorn.jar --whatamidoing
Unrecognized option: --whatamidoing
usage: utility-name
 -d,--directory <arg>   directory to serve from & store to ($PWD if
                        unspecified)
 -p,--port <arg>        port number (auto-picked if unspecified)
```

#### Running it
```
$ mkdir /tmp/eekhoorn

$ java -jar build/libs/eekhoorn.jar -d /tmp/eekhoorn -p 9090
2020-03-03 14:39:38.203:INFO::main: Logging initialized @141ms
2020-03-03 14:39:38.236:INFO:oejs.Server:main: jetty-9.3.z-SNAPSHOT, build timestamp: 2019-11-05T19:00:51+01:00, git hash: d7dd68d6e9d8ff06a0130e46886c2db5d70784c1
2020-03-03 14:39:38.266:INFO:oejs.AbstractConnector:main: Started ServerConnector@573fd745{HTTP/1.1,[http/1.1]}{localhost:9090}
2020-03-03 14:39:38.266:INFO:oejs.Server:main: Started @207ms
```
Interrupt with ctrl-c.


### Get some content to put into Eekhoorn

```
$ wget https://interactive-examples.mdn.mozilla.net/media/examples/flower.webm
```

### PUT it

Also pass the Content-Type header we'd like to see in the response later when GETing this resource.
Note that the API endpoint is at the /eikel path, but anything after that is free form.
```
$ curl -s -S -v \
 --user appel:appel --basic \
 -T flower.webm \
 -H "X-Eekhoorn-Store-Header-Content-Type: video/webm" \
 -H "X-Eekhoorn-Store-Header-Favourite-Condiment: peanutbutter" \
 http://127.0.0.1:9090/appelflap/eikel/what/ever/path/I/want/flower.webm

*   Trying 127.0.0.1:9090...
* TCP_NODELAY set
* Connected to 127.0.0.1 (127.0.0.1) port 9090 (#0)
> PUT /eikel/what/ever/path/I/want/flower.webm HTTP/1.1
> Host: 127.0.0.1:9090
> User-Agent: curl/7.66.0
> Accept: */*
> X-Eekhoorn-Store-Header-Content-Type: video/webm
> X-Eekhoorn-Store-Header-Favourite-Condiment: peanutbutter
> Content-Length: 554058
> Expect: 100-continue
> 
* Mark bundle as not supporting multiuse
< HTTP/1.1 100 Continue
* We are completely uploaded and fine
* Mark bundle as not supporting multiuse
< HTTP/1.1 204 No Content
< Date: Tue, 03 Mar 2020 14:24:17 GMT
< Server: Jetty(9.3.28.v20191105)
< 
* Connection #0 to host 127.0.0.1 left intact
```

### Can we GET it?

```
$ curl -v -s -S http://127.0.0.1:9090/appelflap/eikel/what/ever/path/I/want/flower.webm | wc -c
*   Trying 127.0.0.1:9090...
* TCP_NODELAY set
* Connected to 127.0.0.1 (127.0.0.1) port 9090 (#0)
> GET /eikel/what/ever/path/I/want/flower.webm HTTP/1.1
> Host: 127.0.0.1:9090
> User-Agent: curl/7.66.0
> Accept: */*
> 
* Mark bundle as not supporting multiuse
< HTTP/1.1 200 OK
< Date: Tue, 03 Mar 2020 14:27:40 GMT
< Access-Control-Allow-Origin: *
< Access-Control-Expose-Headers: Favourite-Condiment, Content-Type
< Favourite-Condiment: peanutbutter
< Content-Type: video/webm
< Content-Length: 554058
< Server: Jetty(9.3.28.v20191105)
< 
{ [32594 bytes data]
* Connection #0 to host 127.0.0.1 left intact
554058
```

Seems so, and we even got our bytes back down to the last bit. We can also see the Content-Type headers we set during the PUT.

### What have I stored so far?

```
$ curl -s http://127.0.0.1:9090/appelflap/eikel-meta/status
{
  "diskused": 554058,
  "diskfree": 69899911168,
  "disksize": 429494632448,
  "eikels": [{
    "path": "what/ever/path/I/want/flower.webm",
    "headers": {
      "Favourite-Condiment": "peanutbutter",
      "Content-Type": "video/webm"
    },
    "size": 554058,
    "lastmodified": 1583245457
  }]
}
```

Notice that you could (ab)use the headers to record arbitrary metadata, perhaps to aid in site-JS-side garbage collection or content expiry.
If you find yourself doing so please raise your use case as it might be better handled inside Eekhoorn.

The mtime of the response body is already there and will generally reflect the time of upload:
```
$ date --date=@1583245457
Tue 03 Mar 2020 03:24:17 PM CET
```

Which you could use to inform decisions on cleaning up "old" content. Hit count and access timestamp would be nice future metadata features.

### Let's DELETE

```
$ curl --user appel:appel --basic -v -X DELETE http://127.0.0.1:9090/appelflap/eikel/what/ever/path/I/want/flower.webm
*   Trying 127.0.0.1:9090...
* TCP_NODELAY set
* Connected to 127.0.0.1 (127.0.0.1) port 9090 (#0)
> DELETE /eikel/what/ever/path/I/want/flower.webm HTTP/1.1
> Host: 127.0.0.1:9090
> User-Agent: curl/7.66.0
> Accept: */*
> 
* Mark bundle as not supporting multiuse
< HTTP/1.1 204 No Content
< Date: Tue, 03 Mar 2020 14:31:16 GMT
< Server: Jetty(9.3.28.v20191105)
< 
* Connection #0 to host 127.0.0.1 left intact
```

### Let's NUKE all state

Sadly, `NUKE` is not an HTTP verb, but here's how to nuke all state. **This deletes everything in the directory you passed to Eekhoorn with `-d`!**:

```
curl -s -S -v -X POST http://127.0.0.1:9090/appelflap/eikel-meta/nuke
*   Trying 127.0.0.1:9090...
* TCP_NODELAY set
* Connected to 127.0.0.1 (127.0.0.1) port 9090 (#0)
> POST /meta/nuke HTTP/1.1
> Host: 127.0.0.1:9090
> User-Agent: curl/7.66.0
> Accept: */*
> 
* Mark bundle as not supporting multiuse
< HTTP/1.1 204 No Content
< Date: Tue, 03 Mar 2020 14:31:47 GMT
< Server: Jetty(9.3.28.v20191105)
< 
* Connection #0 to host 127.0.0.1 left intact
```