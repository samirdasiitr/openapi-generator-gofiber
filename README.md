# OpenAPI Generator for the go-fiber library

## Overview
This project adds the missing support for generating gofiber server stack.

## Features
It generates fully functioning server stack, other main features:
* Generated code is in controller-delegate pattern.
* Logstash style logging.
* Generates GORM support (requires vendorExtensions to openapi spec)

## How do I use this?
### Run
```shell
export GO_POST_PROCESS_FILE="/Users/<username>>/workspace/go/bin/goimports -w"
java -cp "build/tools/openapi-generator-gofiber-generator-0.0.1.jar:build/tools/openapi-generator-cli-5.4.0.jar" \
	org.openapitools.codegen.OpenAPIGenerator generate -g go-fiber \
		-o build  \
		-e pebble \
		--package-name server \
		--enable-post-process-file \
		--additional-properties "enumClassPrefix=true" \
		--global-property="skipFormModel=false"
```

### main.go
```java
package main

import (
	"log"
	"time"

	sw "github.com/GIT_USER/GIT_REPO_ID/server"
	"github.com/gofiber/fiber/v2"
)

func main() {
	sw.SetUpLogger("TESTAPP")
	logger := sw.GetLogger()

	config := fiber.Config{
		ReadTimeout: time.Second * time.Duration(10),
	}

	app := fiber.New(config)
	router := sw.NewRouter(app)
    
    // inject service code    
    router.SetPetsApiDelegate(&service.PetsApiDelegate{})
        
	log.Fatal(router.Listen(":8080"))
}

```

to inject real service code, implement the delegate interfaces.
```java
type PetsApiDelegate interface {
	CreatePets(ctx *fiber.Ctx, dbConn *gorm.DB, logger *Logger) (Response, error)

	// limit is query parameter
	ListPets(ctx *fiber.Ctx, dbConn *gorm.DB, logger *Logger, limit *int32) (Response, error)

	// petId is path parameter (Required)
	ShowPetById(ctx *fiber.Ctx, dbConn *gorm.DB, logger *Logger, petId *string) (Response, error)
}

```
