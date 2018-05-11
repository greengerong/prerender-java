Prerender Java [![Build Status](https://travis-ci.org/greengerong/prerender-java.png)](https://travis-ci.org/greengerong/prerender-java)
===========================

Are you using backbone, angular, emberjs, etc, but you're unsure about the SEO implications?

Use this java filter that prerenders a javascript-rendered page using an external service and returns the HTML to the search engine crawler for SEO.

`Note:` If you are using a `#` in your urls, make sure to change it to `#!`. [View Google's ajax crawling protocol](https://developers.google.com/webmasters/ajax-crawling/docs/getting-started)

`Note:` Make sure you have more than one webserver thread/process running because the prerender service will make a request to your server to render the HTML.

1:Add this line to your web.xml:

    <filter>
          <filter-name>prerender</filter-name>
          <filter-class>com.github.greengerong.PreRenderSEOFilter</filter-class>
          <init-param>
              <param-name>prerenderToken</param-name>
              <param-value>[get from prerender: https://prerender.io/]</param-value>
          </init-param>
      </filter>
      <filter-mapping>
          <filter-name>prerender</filter-name>
          <url-pattern>/*</url-pattern>
      </filter-mapping>

2:add dependency on your project pom:

    <dependency>
      <groupId>com.github.greengerong</groupId>
      <artifactId>prerender-java</artifactId>
      <version>1.6.4</version>
    </dependency>

## How it works
1. Check to make sure we should show a prerendered page
	1. Check if the request is from a crawler (`_escaped_fragment_` or agent string)
	2. Check to make sure we aren't requesting a resource (js, css, etc...)
	3. (optional) Check to make sure the url is in the whitelist
	4. (optional) Check to make sure the url isn't in the blacklist
2. Make a `GET` request to the [prerender service](https://github.com/prerender/prerender)(phantomjs server) for the page's prerendered HTML
3. Return that HTML to the crawler

## Customization

### crawlerUserAgents
example: someproxy,someproxy1

### whitelist

### blacklist

### forwardedURLHeader
Important for servers behind reverse proxy that need the public url to be used for pre-rendering.
We usually set the original url in an http header which is added by the reverse proxy (similar to the more standard `x-forwarded-proto` and `x-forwarded-for`)

### protocol
If you specifically want to make sure that the Prerender service queries using https or http protocol, you can set the init-param `protocol` to `https` or `http` respectively.

### Using your own prerender service

If you've deployed the prerender service on your own, set the `PRERENDER_SERVICE_URL` environment variable so that this package points there instead. Otherwise, it will default to the service already deployed at `http://service.prerender.io/`

	$ export PRERENDER_SERVICE_URL=<new url>

Or on heroku:

	$ heroku config:add PRERENDER_SERVICE_URL=<new url>

As an alternative, you can pass `prerender_service_url` in the options object during initialization of the middleware

``` xml
 config filter init param with "prerenderServiceUrl";
```

### prerender service token

If you want to use token with the prerender service, you can config it.


``` xml
 config filter init param with "prerenderToken";
```


### prerender event handler

If you want to cache the caching, analytics, log or others, you can config it. It should be instance of "com.github.greengerong.PreRenderEventHandler"


``` xml
 config filter init param with "preRenderEventHandler";
```


## Testing

If your URLs use a hash-bang:

    If you want to see `http://localhost:3000/#!/profiles/1234`
    Then go to `http://localhost:3000/?_escaped_fragment_=/profiles/1234`

If your URLs use push-state:

    If you want to see `http://localhost:3000/profiles/1234`
    Then go to `http://localhost:3000/profiles/1234?_escaped_fragment_=`
    

project demo test url:

    http://localhost:8080/test/?_escaped_fragment_=
    
## License

The MIT License (MIT)