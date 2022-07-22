# Description

Consider the following openapi spec fragment corresponding to an arbitrary upload endpoint:
```yaml
paths:
  /upload:
    post:
      operationId: "upload"
      requestBody:
        content:
          multipart/form-data:
            schema:
              properties:
                file:
                  format: "binary"
                  type: "string"
              type: "object"
#            encoding:
#              file:
#                contentType: "*/*"
        description: "(to be documented)"
      responses:
        "204":
          description: "(to be documented)"
      summary: "upload a file"
```
A `vertx-web-openapi` backend will raise a 400 error when used with Swagger UI. 

Please note that this spec was working properly with Vert.x 3 (vertx-web-api-contract) and swagger UI.

# How to reproduce

Run:
```
mvn clean compile exec:java
```
Then open:
http://localhost:8888/docs

And upload a file.

# Error

Vert.x implicitly assigns the `application/octet-stream` content type to the endpoint,
whereas Swagger UI will provide a real content type (like `application/vnd.oasis.opendocument.spreadsheet`).


With that spec we get this 400 error...:
```
[Bad Request] File with content type \Qapplication/octet-stream\E and name file is missing
```
... from:
https://github.com/vert-x3/vertx-web/blob/2b7c705faf60b9b670549d25b06479308b358289/vertx-web-validation/src/main/java/io/vertx/ext/web/validation/RequestPredicate.java#L45

# Workaround tentative

If we uncomment the encoding part of the spec to specify the `*/*` mime type, then the backend fails with the following error:

```
[Bad Request] File with content type \Q/\E. and name file is missing
```

Because Vert.x doesn't truly accept such a complete wildcard mime type (it seems that only the sub-type can be a wildcard):

https://github.com/vert-x3/vertx-web/blob/2b7c705faf60b9b670549d25b06479308b358289/vertx-web-openapi/src/main/java/io/vertx/ext/web/openapi/impl/OpenAPI3Utils.java#L67

# Discussion

According to a discussion in the OpenAPI specification github repo (discussion including Mike Ralphson, a major OpenAPI contributor), it seems that
1. `*/*` is an acceptable value 
2. `application/octet-stream` is not equivalent to `*/*`:

https://github.com/OAI/OpenAPI-Specification/issues/1692#issuecomment-423610318

But it is true that, when omitted in the spec, the `application/octet-stream` mime type is implied:

https://github.com/OAI/OpenAPI-Specification/blob/main/versions/3.0.1.md#special-considerations-for-multipart-content

So I think this Vert.x code is correct:

https://github.com/vert-x3/vertx-web/blob/2b7c705faf60b9b670549d25b06479308b358289/vertx-web-openapi/src/main/java/io/vertx/ext/web/openapi/impl/MultipartFormBodyProcessorGenerator.java#L50

Reading the RFC of the Content-Type field, it is not absolutely obvious if `*` and `/` characters are allowed in a type or a sub-type: 

https://datatracker.ietf.org/doc/html/rfc2045#section-5.1

# Solution

So I think the safest approach is to modify this Vert.x code to handle the `*/*` specific case (in addition to the `/*` specific case):

https://github.com/vert-x3/vertx-web/blob/2b7c705faf60b9b670549d25b06479308b358289/vertx-web-openapi/src/main/java/io/vertx/ext/web/openapi/impl/OpenAPI3Utils.java#L67

