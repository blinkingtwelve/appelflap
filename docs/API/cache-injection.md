# Cache injection

It could be helpful to the webapp if it knew about any cache updates staged by Appelflap.

Furthermore, since it will be hard for the webapp to relinquish locks in the cache metadata administration (as outlined in [Cache locking](cache-locking.md) - pending more research), likely a restart of GeckoView is needed to inject caches. It would be nice if the webapp could configure Appelflap to 1) not perform the brief interruption at will, 2) do it at a specific moment at the webapp's behest.


# Operations - Entity: Injection lock

If the entity exists, Appelflap will withhold on restarting Geckoview to free locks and inject staged caches.
When the entity doesn't exist, Appelflap will still inject staged caches (as long as they match the subscriptions) when Geckoview is started — this happens, for instance, when the Android app itself is started.

## Creating the injection lock

### Request
```
PUT /appelflap/ingeblikt/injection-lock
```

#### Request headers
- `Authorization` - see Authentication

#### Request body

Ignored.

### Response

#### Response status
- 200/201 on success
- 401 when authentication has failed

Generally, there will be an accompanying human-readable reason phrase in the response.

#### Response body

Empty.

## Deleting the injection lock

### Request
```
DELETE /appelflap/ingeblikt/injection-lock
```
#### Request headers
- `Authorization` - see Authentication

#### Request body

Ignored.

### Response

#### Status
- 200 on success
- 401 when authentication has failed

Generally, there will be an accompanying human-readable reason phrase in the response.

#### Body

Empty.

