# Operations - Entity: Cache


## In general: URL-safe Base64-encoding

Some URL components need to be supplied in urlsafe base64 encoding.
A good test string is `ts?`. A non-URLsafe Base64-encode of that string will include a slash (`dHM/`) while an URL-safe encoding will not (`dHM_`).

### URL-safe Base64: In the browser

You'll need to run some variation of
```javascript
btoa(String.fromCodePoint(...(new TextEncoder('utf-8').encode('subjects?')))).replaceAll('+', '-').replaceAll('/', '_')
```

Resource: [WhatWG](https://github.com/whatwg/html/issues/351#issuecomment-158914552)

### URL-safe Base64: In a Unix-y shell

```bash
echo -n 'ts?' | base64 -w0 | tr '+/' '-_'
```

## Creating/updating a cache publication

Appelflap will pack up contents of the designated cache. Appelflap can't fully snapshot the designated cache's state at the time of request (unless it takes extreme measures, but we don't want that). Thus, for predictable results, the webapp shouldn't modify the cache while Appelflap is packing it up.

### Request

```
PUT /appelflap/ingeblikt/publications/{type}/{web-origin}/{cache-name}/{version}?contentionstrategy=(NOOP|ENGINE_REBOOT|ENGINE_REBOOT_UPON_CONTENTION)
```

#### URL components


- `type` - type of object - `SWORK` or `CACHE` for serviceworker or cache, respectively.
- `web-origin` - the origin domain of the cache object or the scope of the serviceworker, urlsafe base64-encoded. Typically, this will be the (base64 url-safe encoded) value of `self.origin`.
- `cache-name` - Cache name or serviceworker URL, urlsafe base64-encoded.
- `version` - Version, optional, an integer between 0 and 2^63 -1. Absent a user-specified version, the version will be autoderived from the most recent `Date` HTTP response header found in the cache in re, denoted as seconds since the Unix epoch. If no version is specified and no version can be autoderived, packing up will fail.
- `contentionstrategy` - Optional query parameter for choosing a contention strategy:
  - `NOOP` - Don't do anything. If the cache state (as observable by Appelflap) is incoherent, a 503 error will be returned.
  - `ENGINE_REBOOT` - Shut down GeckoView (destroying the webapp context - this means an in-webapp caller will never see the response), pack up the settled/stable state, and relaunch GeckoView with its session state restored.
  - `ENGINE_REBOOT_UPON_CONTENTION` - Try reading the cache, only resorting to ENGINE_REBOOT behaviour if contention is taking place. This is the default.

#### Request headers
- `Authorization` - see Authentication

#### Request body

Ignored.

### Response

#### Response status
- 200/201 on success
- 409 when a packing action is already in progress for this cache
- 400 when the version supplied is invalid
- 401 when authentication has failed
- 404 when Appelflap can't find the designated cache
- 503 when Appelflap cannot comply (contention issues)

Generally, there will be an accompanying human-readable reason phrase in the response.

#### Response body

Empty.

## Downloading a cache publication

### Request

```
GET /appelflap/ingeblikt/publications/{type}/{web-origin}/{cache-name}/{version}
```

#### URL components
See PUT.


#### Request headers

None required.

#### Request body

Ignored.

### Response

#### Response status
- 200 if it can be found
- 400 when the version supplied is invalid
- 404 when Appelflap can't find the designated cache

Generally, there will be an accompanying human-readable reason phrase in the response.

#### Response body

The packed up cache, if it can be found.

#### Response headers

- `Content-Type`: "application/zip"
- `Content-Disposition`, "attachment; filename="suitable-filename" with suitable-filename derived from origin, cachename, and version.

## Deleting a cache publication

### Request

```
DELETE /appelflap/ingeblikt/publications/{type}/{web-origin}/{cache-name}/{version}
```

#### URL components
See PUT

#### Request headers
- `Authorization` - see Authentication

#### Request body

Ignored.

### Response

#### Response status
- 200/201 on success
- 409 when an operation is already in progress for this cache
- 401 when authentication has failed
- 404 when there is no such publication

Generally, there will be an accompanying human-readable reason phrase in the response.

#### Response body

Empty.

## Retrieving publication information

A one-stop endpoint for retrieving information on all locally stored cache publications (both those created on the device and those imported from other devices).

### Request
```
GET /appelflap/ingeblikt/publications
```

#### Request headers

None required.

### Request body
Ignored.

### Response

#### Response headers
- `Content-Type`: "application/json"

#### Response status
- 200 on success

#### Response body
A self-explanatory JSON document containing locally stored publication bundles (created by the device and acquired from other devices), in the following structure (example):

```
{
    "bundles": [
        {
            "bundle": {
                "type": "CACHE",
                "origin": "https://learn.canoe-engineering.temp.build",
                "cachename": "pages-cache",
                "version": 1
            },
            "size": 21933,
            "mtime": 1614261272000
        },
        {
            "bundle": {
                "type": "CACHE",
                "origin": "https://learn.canoe-engineering.temp.build",
                "cachename": "pages-cache",
                "version": 2
            },
            "size": 21933,
            "mtime": 1614261270000
        },
        {
            "bundle": {
                "type": "CACHE",
                "origin": "https://learn.canoe-engineering.temp.build",
                "cachename": "manifest-cache",
                "version": 1
            },
            "size": 11961,
            "mtime": 1614261674000
        },
            "bundle": {
                "type": "SWORK",
                "origin": "https://learn.canoe-engineering.temp.build",
                "name": "https://learn.canoe-engineering.temp.build/sw.js",
                "version": 2
            },
            "size": 367733,
            "mtime": 1624537050000
    }
    ]
}
```

Size is in bytes, mtime is a Unix timestamp with millisecond resolution (but note that the underlying **emulated** FAT32 filesystem from which these are derived only has 2000ms resolution for the modification timestamp!).
