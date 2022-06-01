# Determining endpoint properties

Some vital information is passed to the browser context so that it can find and address the API.
In debug builds, port number and credentials for HTTP Basic auth are 9090 and 'appel:appel', respectively.
In production builds, these are randomized (port number out of necessity, credentials for the purpose of security).
In both cases, these facts are exposed to the page and serviceworker contexts (but not to other Android apps) through an extension URI.

Before any operation, the webapp client needs to retrieve the current port number and credentials.
These are not expected to change over the lifetime of the application's Android activity, but as the webapp has no idea about Android activity lifetimes,
the safe behaviour is to always rederive them when needed, and for failed requests to check the used information against the current facts, as the server moving ports could have been the cause of the failure — a rare circumstance, unless the app formulates requests far in advance of dispatching them.

## Deriving the endpoint properties

```javascript
fetch('moz-extension://appelflap/serverinfo.json').then(resp => resp.json())
```

This results in an object looking like this:
```json
{
  "username": "appel",
  "password": "appel",
  "port": 9090
}
```

Creating an HTTP Basic authentication header to attach to requests to privileged parts of the API is straightforward:

```javascript
{Authorization: "Basic " + btoa(`${username}:${password}`)}
```

It's fine to attach this header to all appelflap HTTP API requests, regardless of whether they actually require authentication.