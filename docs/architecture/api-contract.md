# API Contract: Architeezy Archi Connector

## Overview

The plugin communicates with the Architeezy server exclusively via a RESTful HTTP API over HTTPS.
All requests that require authentication include a Bearer token in the `Authorization` header.
Responses use **HAL+JSON** (Hypertext Application Language) — a JSON convention that embeds
hypermedia links alongside data to decouple the client from hard-coded URL structures.

## Response Format (HAL+JSON)

A typical HAL response for a collection looks like:

```json
{
  "_embedded": {
    "models": [ { ... }, { ... } ]
  },
  "_links": {
    "self": { "href": "https://server/api/models?page=0&size=20" }
  },
  "totalElements": 42,
  "totalPages": 3,
  "number": 0
}
```

Individual resource objects include a `_links` section with navigation relations. The plugin
extracts the following link relations from model objects:

- `self` — the model's canonical API URL, used for updates and deletion.
- `content` — the URL for the model's binary content. The relation may be rendered either as a
  single link (the `/api/models/{id}/content` endpoint) or as an array of per-format entries; when
  multiple are present the plugin prefers `"title": "ArchiMate"`. URI template suffixes (e.g.,
  `{?format,inline}` or `{&inline}`) are stripped before use. If the link is missing entirely the
  plugin falls back to `${self}/content?format=archimate`.

## Endpoints

### List Models

|                   |                                                |
| ----------------- | ---------------------------------------------- |
| **Method**        | `GET`                                          |
| **Path**          | `/api/models`                                  |
| **Query Params**  | `page` (zero-based), `size` (items per page)   |
| **Auth Required** | Depends on server configuration                |
| **Response**      | HAL collection; models in `_embedded.models[]` |

Each model object contains: `id`, `slug`, `name`, `description`, `lastModificationDateTime`, plus
nested entity references `scope` (`{id, slug, name}`), `project` (`{id, slug, version, name}`) and
`creator` (`{id, name}`), and `_links` with `self` and `content` relations. The plugin reads the
creator's `name` as the displayed author and keeps the project/scope slugs around to build the
browser URL.

### Get Model Metadata

|                   |                         |
| ----------------- | ----------------------- |
| **Method**        | `GET`                   |
| **Path**          | `/api/models/{id}`      |
| **Auth Required** | Yes                     |
| **Response**      | Single HAL model object |

Used by the update check service to compare server-side `lastModified` without downloading content.

### Download Model Content

|                   |                                                                      |
| ----------------- | -------------------------------------------------------------------- |
| **Method**        | `GET`                                                                |
| **URL**           | Content URL extracted from the model's `_links.content` HAL relation |
| **Auth Required** | Yes                                                                  |
| **Response**      | Raw binary bytes (`.archimate` XMI file)                             |

The URL is taken directly from the HAL response, not constructed by the client.

### List Projects

|                   |                                                                            |
| ----------------- | -------------------------------------------------------------------------- |
| **Method**        | `GET`                                                                      |
| **Path**          | `/api/projects`                                                            |
| **Query Params**  | `size=100` (fetch all in one page)                                         |
| **Auth Required** | Yes                                                                        |
| **Response**      | HAL collection; projects in `_embedded.projects[]` or similar embedded key |

Each project contains: `id`, `name`.

### Create Model Entry

|                   |                                                                                |
| ----------------- | ------------------------------------------------------------------------------ |
| **Method**        | `POST`                                                                         |
| **Path**          | `/api/models`                                                                  |
| **Auth Required** | Yes                                                                            |
| **Request Body**  | JSON: `{ "name": "...", "description": "..." }`                                |
| **Response**      | HAL model object for the newly created entry (with `self` and `content` links) |

Used by the Publish flow to register a new model entry before uploading its content.

### Update Model Content

|                   |                                                            |
| ----------------- | ---------------------------------------------------------- |
| **Method**        | `PUT`                                                      |
| **URL**           | The `self` URL of the model (from HAL links)               |
| **Auth Required** | Yes                                                        |
| **Request Body**  | Raw binary bytes; `Content-Type: application/octet-stream` |
| **Response**      | HTTP 200 on success                                        |

Used to upload the latest serialized model content after a successful local save.

### Export Model (Multipart)

|                   |                                                                                                      |
| ----------------- | ---------------------------------------------------------------------------------------------------- |
| **Method**        | `POST`                                                                                               |
| **Path**          | `/api/models`                                                                                        |
| **Auth Required** | Yes                                                                                                  |
| **Request Body**  | `multipart/form-data` with two parts:                                                                |
|                   | `entity` — JSON part: `{ "projectId": "...", "name": "...", "description": "..." }`                  |
|                   | `content` — binary part: the `.archimate` file bytes, filename `{modelName}-{date}-{time}.archimate` |
| **Response**      | HAL model object for the new entry                                                                   |

Used by the Export wizard to create and upload a model in a single request, associating it with the
selected project.

### Delete Model

|                   |                             |
| ----------------- | --------------------------- |
| **Method**        | `DELETE`                    |
| **URL**           | The `self` URL of the model |
| **Auth Required** | Yes                         |
| **Response**      | HTTP 204 on success         |

Currently available in the infrastructure layer but not exposed in the plugin UI.

## Error Responses

The plugin interprets HTTP status codes to provide user-facing error messages:

| Status          | Interpretation                                                                   |
| --------------- | -------------------------------------------------------------------------------- |
| 401             | Unauthorized — token missing or invalid; trigger token refresh or re-auth prompt |
| 403             | Forbidden — user lacks permission for this resource                              |
| 404             | Not Found — the model or project no longer exists                                |
| 5xx             | Server Error — show a generic server error message                               |
| Network failure | Connection refused, timeout, etc. — show a connectivity error                    |

## HTTP Client Characteristics

- The plugin uses the Java 11+ built-in HTTP client (`java.net.http`). No third-party HTTP library
  is included.
- All requests are synchronous from the caller's perspective and are always made on a background
  thread (never on the SWT UI thread).
- The `Authorization: Bearer {accessToken}` header is added to every authenticated request.
- JSON parsing is performed with hand-written string extraction (no external JSON library), keeping
  the OSGi dependency footprint minimal.
