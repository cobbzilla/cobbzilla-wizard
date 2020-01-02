# ApiRunner
ApiRunner is a set of Java tools to facilitate integration testing of REST APIs that use the cobbzilla-wizard framework
for running the API and populating a data model.

ApiRunner uses a declarative approach to API testing, while allowing highly dynamic behavior and state management.

## JUnit
The easiest way to use ApiRunner is to create a JUnit test class that is a subclass of `ApiModelTestBase`, and
call modelTest, for example:

```code
import org.apache.commons.io.FileUtils;
import org.cobbzilla.wizard.server.config.factory.ConfigurationSource;
import org.cobbzilla.wizardtest.resources.AbstractResourceIT;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class FooTest extends AbstractResourceIT {

    @Override protected ConfigurationSource getConfigurationSource() {
        return () -> new FileInputStream("/path/to/config.yml");
    }

    @Test public void testSomeApiStuff      () throws Exception { runScript("basic_test"); }
    @Test public void testSomeOtherApiStuff () throws Exception { runScript("some_dir/another_test"); }
}
```

When JUnit runs, it will call methods testSomeApiStuff and testSomeOtherApiStuff.
 
For each of these tests, `runScript` will run the ApiScripts contained in each file. The default resolution is to append `.json` and 
try to load from the current classloader. In the above, the ApiRunner would expect to find `basic_test.json` in the base resource directory
and `some_dir/another_test.json` in 
The default "relative path" for test scripts is `models/tests`, and must be visible on the classpath (via `ClassLoader.getResourceAsStream`, using the JUnit class's class-loader).

Subclasses can override the default include resolution process, for example to load from a directory on the filesystem
 instead of the classpath:
```code
    import org.apache.commons.io.FileUtils;
    ...
    public class FooTest extends AbstractResourceIT {
        ...
        protected String resolveInclude(String path) {
            try {
                return FileUtils.readFileToString(new File("/tmp/my_tests/"+path+".json"), "UTF-8");
            } catch (IOException e) {
                throw new IllegalStateException("resolveInclude("+path+"): "+e, e);
            }
        }
    }
```
 
# ApiScript
An ApiScript is a JSON file of type ApiScript[]. The root element is an array ApiScript objects.

The array may be empty, in which case the running the script is a "no-op".

If the script array contains ApiScript objects, each such object represents an API request/response, and some additional associated data.

It's design supports the idea that simple things should be easy, and complex things should be possible.

# ApiScriptRequest
The `request` portion of an ApiScript contains minimally a `uri` field. This is taken to be relative to an API "base"
which is set elsewhere in the framework, and provided as a context variable to the ApiRunner.

## GET requests
Just specify the `uri` property, nothing else. ApiRunner will execute the GET request against the API, and return the
JSON object, optionally storing it in a context variable if the `store` property is set on the response.

    {"request": "/me"}

The ApiRunner will verify that the response had a 200 status, but otherwise does nothing with the response.

## POST requests
               
To `POST` data, add an `entity` property:

    {
        "request" {
                "uri": "/account/info",
                "entity": {
                        "username": "jsmith123",
                        "some_prop": "some_value"
                }
            }
        }
    }

## Other requests
For requests with a method that is not `GET` or `POST`, add a `method` property.
The method name is case-insensitive, use capitals or lowercase at your preference.

For example:

    {
        "request" {
                "uri": "/account/preferences",
                "method": "put",
                "entity": [
                        {"favorite_fruit": "apple"},
                        {"best_movie": "Logan's Run"}
                ]
            }
        }
    }

## ApiSession
As the ApiRunner runs, each connection to the backend API server is done in the context of a session, or no session.
Without a session, (or if the `request.session` property is `new`), the ApiRunner will not send an API credential token
with any request.

For each ApiScript that the ApiRunner runs, a single session will be used. The ApiScript can specify a
 `request.session` of `new` to indicate that any previous session should not be used, and the request should be made
 without any authentication added.
 
 Once a session is established, or set, the corresponding session token will be sent to the API server,
  until some subsequent ApiScript sets the `request.session` property to a different session, or to `new`.
 
A session can be established by an ApiScript in two ways:  

### Set session from an ApiScriptResponse
    {
        "request" {
                "uri": "/login",
                "method": "put",
                "entity": [
                        {"username": "jsmith123"},
                        {"password": "B8F8UJ7PZX93AF9M4EJ8O9QXY"}
                ]
            }
        },
        "response": {
            "sessionName": "userSession",
            "session": "token"
        }
    }

In the above, if the `/login` request succeeds, then the ApiRunner will create a new API session and continue to use
that session for future requests, until changed.
The session will be saved by the ApiRunner under the name `userSession`. The `"session": "token"` part means that, in the JSON returned from the 
 `/login` request, the `token` property contains the session token. 

In order to ensure this token is returned to the server in the appropriate header, API tests requests require
 small subclass of `ApiClientBase` to tell ApiRunner which HTTP header to use for sending the session token.
 
 Do this by overriding the `getApi` method and providing your subclass which has a `getTokenHeader` method defined.
```code
import org.cobbzilla.wizard.client.ApiClientBase;
...

public class FooTest extends AbstractResourceIT {

    @Override public ApiClientBase getApi() {
        return new ApiClientBase(super.getApi()) {
            @Override public String getTokenHeader() { return "X-MyApp-Session"; }
        };
    }
```

If session management requires more complex processing, override the `beforeSend` method in your client class for full control:
```code
...
public class FooTest extends AbstractResourceIT {

    @Override public ApiClientBase getApi() {
        return new ApiClientBase(super.getApi()) {
            @Override protected HttpRequestBase beforeSend(HttpRequestBase request) {
                // ... adjust request as needed / add authentication
                return request;
            }
        };
    }
``` 

### Set session by name in an ApiScriptRequest
An ApiScriptRequest can set the session for a request. When the session is set, it will be used on subesquent requests,
unless they specify a different session, or a new session.

For example, let's say the current session was created at login and named `userSession`. Then our test script created
a second user and logged in as them, and saved it with the session name `secondUser`. The ApiRunner's current session
is thus `secondUser`, but later in the test we want to make an API call as the first user. 

A GET using a named session can switch between sessions:
    {
        "request" {
                "uri": "/account/something",
                "session": "userSession"
            }
        }
    }

Setting the `response.session` property to `userSession` means that ApiRunner will make the GET request using the
authentication credentials for that session, instead of the `secondUser` session.
  

## ApiScriptResponse
Whereas an ApiScriptRequest represents instructions to ApiRunner to do actively very specific things to an API server, 
ApiScriptResponse reads a response and applies various tests and checks to verify that everything is as expected before continuing.

Things that will cause ApiRunner to fail an ApiScript:

  * By default, only HTTP status 200 is considered successful.
  * If `response.status` (integer) was set, and the HTTP status received does not match its value
  * If `response.okStatuses` (array of integers) was set, and the HTTP status received does not match any of these values
  * If any of the tests in the `check` return `false` or throw an exception   

### The `check` tests
The `response.check` property, if present, is an array of tests. Each test is a JavaScript expression. The variables
available within the JavaScript context are:

  * All objects that have been saved via the `response.store` property
  * All variables in the Map returned from getConfiguration().getServerEnvironment() in the JUnit test class (a subclass of `AbstractResourceIT`)
  * A special variable called `configuration` references the object returned by getConfiguration()
  * A special variable called `json` references the object returned by the API for the current ApiScript.

### Handlebars + JavaScript
ApiRunner has a dual-context system: Handlebars *and* JavaScript are used to give tests the flexibility and expressive power 
that serious REST API testing demands.

Handlebars is applied to all properties in the ApiScript before it is run.
For example `request.url` will often contain variables from previous ApiScripts, and you can also use Handlebars within
the JavaScript `check` conditions.

The `check` conditions are JavaScript expressions, which are evaluated as booleans.
Any test that returns false or throws an exception will cause the JUnit test to fail.

Note that the `json` variable is not available in Handlebars contexts used in the `request` section, since it references the response object.

Let's say we previously fetched the current user via  `{"request":{"uri":"/me"}, "response":{"store":"someUser"}}`,
 so the current user is saved in the `someUser` variable. Then we fetch the user via the `/users/` API, using the id,
 and verify the username is the same.

    {
        "request": { "uri": "/users/{{someUser.uuid}}/" },
        "response": {
            "store": "checkUser",
            "check": [ {"condition": "checkUser.getName() === someUser.getName()"} ]
        } 
    },  // ...more script items...


Because we can also use Handlebars within JavaScript, an equivalent of above the would be:

    {
        "request": { "uri": "/users/{{someUser.id}}/" },
        "response": {
            "store": "checkUser",
            "check": [
                {"condition": "'{{checkUser.name}}' === '{{someUser.name}}'"}
            ]
        } 
    },  // ...more script items...

The Handlebars is evaluated first, so the JavaScript test is now a comparison of two string literals.

Handlebars object notation is less verbose than Java/JavaScript, especially for heavily nested objects.
Using Handlebars expressions within the JavaScript tests can often aid in test clarity.

