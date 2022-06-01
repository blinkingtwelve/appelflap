# Downloads

The webapp can see and manage what has been downloaded.


## Downloads info

### Request

```
GET /appelflap/downloads
```

#### Request headers
- `Authorization` - see Authentication

#### Body

Ignored

### Response body

A JSON document like so:

```json
{
  "downloads": [
    {
      "url": "http://127.0.0.1:8010/some/url",
      "filename": "arduino.pdf",
      "size": 33516,
      "mtime": 1628696541000,
      "mimetype": "application/pdf",
      "ID": "699ebf705f2ca233e7afe075d47d0a36.fac99b5e2863042e5dd5f5205b2e2109"
    }
  ]
}
```

- `mtime` is in milliseconds since the Unix epoch. If a server `Date` header was available, then it's derived from there. Otherwise, it's derived from the local clock via the filesystem.
- `ID` is an identifier used when performing an action on a download
- `mimetype` is derived from the HTTP response's Content-Type header, with fallback to trying to guess it from the filename in the Content-Disposition, if any, with fallback to trying to guess it from the URL itself. If all that fails, this field will be `null`.


## Performing actions on the downloads

The webapp can perform several actions on the downloads, by passing the `ID` obtained from a listing.

### Request

```
POST /appelflap/downloads/{ID}?action=(delete|open|share)
```

- `delete` deletes the download
- `open` opens the file, via Android's mechanisms thereto. Success depends on whether a suitable app for opening the file type is installed.
- `share` "shares" the file, via Android's mechanism - the intent for this is to allow users to, for instance, email the download as an attachment, or save it elsewhere.

#### Request headers
- `Authorization` - see Authentication

#### Body

Ignored