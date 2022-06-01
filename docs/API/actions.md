# Operations - Action

Endpoint for enacting actions that would look awkward full-REST.

## Rebooting

This is a debugging/development feature. There is no need for the frontend code to call these endpoints.

- Soft reboot: Closes and restores the session (similar to closing and reopening a tab)
- Hard reboot: Shuts down the GeckoView process and then restores it (similar to closing and reopening a browser application)

These actions will be performed with no regard to the state of the insertion lock.
Any DB locks Geckoview held will be released, giving opportunity for Appelflap to inject caches.
After that, GeckoView will be started afresh, with any new/updated caches available.

### Request
```
POST /appelflap/do/{soft, hard}-reboot
```

#### Request headers
- `Authorization` - see Authentication

#### Body

Ignored

### Response

Irrelevant, as the caller won't be around much longer.


## Launching the Android WiFi enabler/picker

### Request
```
POST /appelflap/do/launch-wifipicker
```

#### Request headers
- `Authorization` - see Authentication

#### Body

Ignored

### Response

If all went well, a 200 status code, and no body.


## Launching the Android Storage manager (for cleanup)

### Request
```
POST /appelflap/do/launch-storagemanager
```

#### Request headers
- `Authorization` - see Authentication

#### Body

Ignored

### Response

If all went well, a 200 status code, and no body.
If the Android version does not support the storage manager cleanup action, a 410 status code is returned.


## Injecting caches imperatively

While Appelflap will autonomously inject caches matching subscriptions while the webapp is not running there may be times when it's desirable for the webapp to poke Appelflap to do the injection.

This endpoint will not honour the injection lock. Thus, the webapp does not need to release the injection lock before calling this endpoint.

To avoid inconsistencies, the injections will be performed after terminating any GeckoView content processes, relaunching the webapp into its last state when done. This means that the webapp will not live to see the response of its request to this endpoint. However, once revived, it can read `/appelflap/ingeblikt/subscriptions` to determine which injections have taken place.

Furthermore, it will only inject caches, not service workers. The webapp can check for queued serviceworker injections by querying `/appelflap/ingeblikt/subscriptions/injectables` and filtering by type. These serviceworkers will be installed when the app boots anew. Calling `window.close()` from the webapp JS context will accomplish an app shutdown, and the next time Appelflap starts, the serviceworkers will be installed. Calling `/appelflap/do/hard-reboot` will accomplish the same, plus the app will be relaunched so that the user does not need to tap the launcher icon themselves.

### Request
```
POST /appelflap/do/inject-caches
```

#### Request headers
- `Authorization` - see Authentication

#### Body

Ignored

### Response

If all went well, a 200 status code, with a JSON body containing an array of results as follows:

```json
{
  "results": [
    {
      "bundle": {
        "type": "CACHE",
        "origin": "http://127.0.0.1:8020",
        "name": "/site/learn",
        "version": 2
      },
      "success": true
    },
    {
      "bundle": {
        "type": "CACHE",
        "origin": "http://127.0.0.1:8020",
        "name": "/site/learn/welcome",
        "version": 123
      },
      "success": true
    }
  ]
}
```

These injections will then also be reflected in `/appelflap/ingeblikt/subscriptions` in the form of updated `injected_version` fields.