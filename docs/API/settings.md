# Settings

Some things are worth configuring at runtime by the webapp.


## UI language

There's a modicum of Appelflap UI elements carrying textual information. We try to minimize it. But there is some.
Here's how to change the language these strings are presented in.

### Request

```
POST /appelflap/settings/ui-language
```

#### Request headers
- `Authorization` - see Authentication

#### Request body

Should be an UTF-8 encoded literal language tag.

When the tag thus declared is deemed invalid (not configured in Appelflap), a 400 Bad Request HTTP status code is returned.

#### Notes
A new language setting is applied only for newly loaded string resources. To guarantee that all UI elements appear in the newly chosen language (to the extent that translations have been provided, of course), the webapp can relaunch the application [using the reboot handler from the actions API](./actions.md).