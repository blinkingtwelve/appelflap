# Operations - Entity: Subscription

For a webapp to receive caches/serviceworkers, or updates to these, it needs to make its desires known to Appelflap, so that it can act on the webapp's behalf. Appelflap will be on the lookout for specified items, and and will inject them into the webapp, replacing any cache already there if it has the same identity. 

There is a single endpoint which may be beGETed and bePUTed.

## Retrieving the subscription set

### Request
```
GET /appelflap/ingeblikt/subscriptions
```

#### Request headers
- `Authorization` - see Authentication

#### Body

Ignored.

### Response

#### Response headers

- `Content-Type: application/json`
- `ETag` - Used to avoid lost writes when PUT-ing

#### Response status
- 200 on success
- 401 when authentication has failed

Generally, there will be an accompanying human-readable reason phrase in the response.

#### Response body
A JSON document containing all subscription data, in the following structure (example):

```
{
    "types": {
        "CACHE": {
                "groups": {
                    "some-web-origin": {
                        "names": {
                            "some-cache-name": {
                                "injection_version_min": 10,
                                "injection_version_max": 20,
                                "p2p_version_min": 200,
                                "p2p_version_max": 888,
                                "injected_version": 12
                            },
                            "another-cache-name": {
                                "injection_version_min": 42,
                                "injection_version_max": 42,
                                "p2p_version_min": 1,
                                "p2p_version_max": 9000,
                                "injected_version": null
                            }
                        }
                    },
                    "some-other-web-origin": {
                        "names": {
                            "yet-another-cache-name": {
                                "injection_version_min": 42,
                                "injection_version_max": 42,
                                "p2p_version_min": 1,
                                "p2p_version_max": 9000,
                                "injected_version": null
                            }
                        }
                    }
                }
            }
        }
    }
```

For serviceworkers, the "groups" key is the scope, for caches, this key is the origin.
For serviceworkers, the "names" key is the serviceworker URL, for caches, this key is the cache name.

Properties of the descriptor object include:  

- `injection_version_min` - the minimum version number (inclusive) of a candidate item for injection
- `injection_version_max` - the maximum version number (inclusive) of a candidate item for injection
- `p2p_version_min` - the minimum version number (inclusive) of a candidate item for acquiring from and distributing to peers
- `p2p_version_max` - the maximum version number (inclusive) of a candidate item for acquiring from and distributing to peers
- `version_injected` - indicates that an injection for this subscription has taken place with the denoted version (null if none has taken place)


#### Notes

The version fields denote ranges of candidate versions. This allows the webapp to implement a scheme to avoid receiving items that are incompatible with its current state, while at the same time acquiring potentially-currently-incompatible items that may be useful in the future, all the while continuing to serve items acquired in the past that may still be useful to peers.

Garbage collection (removing things neither desired for injection nor desired for distribution) is automatic. Basically, if the subscriptions don't say that the item should be kept, it will be GCed.


## Setting the subscription set

Any existing subscription not contained in the submitted set will be deleted.

### Request
```
POST /appelflap/ingeblikt/subscriptions
```

#### Request headers
- `Authorization` - see Authentication
- `Content-Type` - should be `application/json`
- `If-Match` - **Required**; the ETag of the current state (as retrieved earlier with GET). This is used to prevent edit conflicts/lost writes. It is nonconformant to RFC7232 as a) multiple values are not interpreted/allowed, and b) the `"*"` wildcard is explicitly disallowed.

#### Body

A JSON document containing all subscription data, in the format described above.

#### Notes

When PUT-ing, the `injected_version` field serves to enable updating a subscription (for instance, with new p2p-versions) without discarding the Appelflap injection tracking state. When Appelflap would not have any knowledge anymore that an injection of a certain version has taken place, it'd be performed again (subject to availability of cache bundles). For that and other reasons related to the multiple-writers-shared-repository, edits should always be formulated on top of freshly acquired subscription state. If it can be proven that the webapp is basing its edits on stale state, upon PUTing the subscriptions it will be notified of the edit conflict with an HTTP 412 Precondition failed response. The webapp's strategy for writing to this shared repository while preventing overwriting any concurrently made edits should be as follows:

1. Disavow any knowledge of the current state of the subscriptions.
2. Acquire the current state of the subscriptions object by GETing it, and denote the response's `ETag` header value.
3. Apply the desired mutations, taking into account that the subscriptions may not be in the same state the webapp last left them in (for instance, Appelflap may have updated an `injected_version` field of one or more caches).
4. Copy the `ETag` header value from step 2 into the `If-Match` request header.
5. Fire off the PUT-request.
6. If the response indicates success, the new subscription set has been committed. If the response indicates a 412 Precondition Failed error, something has happened to the subscriptions concurrently between step 2 and 5. The webapp should go to step 1, and re-evaluate whether the intended mutations are still desired, and if so, apply (some of) them on top of the updated state as received from Appelflap.


### Response

#### Response headers

- `Content-Type: application/json`
- `ETag` - the new document identity

#### Response status
- 200 on success
- 400 when the body does not conform
- 401 when authentication has failed
- 412 when an edit conflict has arisen (see notes for `If-Match` request header).

Generally, there will be an accompanying human-readable reason phrase in the response.

#### Response body

The new subscriptions, in the format described above. The contents may be ordered differently from what was `PUT`-ed.


## Viewing the planned injections

This is the product of a cross-match of the injection subscriptions with the locally available bundles (as can be queried at `/appelflap/ingeblikt/publications`).
The result could be deduced clientside, but as Appelflap has this information already on hand, it may just as well tell the webapp what it thinks needs injecting.

### Request
```
GET /appelflap/ingeblikt/subscriptions/injectables
```

#### Request headers
- `Authorization` - see Authentication

#### Body

Ignored.

### Response

#### Response headers

- `Content-Type: application/json`

#### Response status
- 200 on success
- 401 when authentication has failed

Generally, there will be an accompanying human-readable reason phrase in the response.

#### Response body
A JSON document describing bundles that are candidate for injection, in the same structure as the bundle index available from `/appelflap/ingeblikt/publications`.