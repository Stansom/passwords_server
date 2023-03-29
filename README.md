# Password Server
## Simple multi-users password manager written in Clojure and Babashka. <br/>
### Depends on:
- #### HTTP Kit
- #### PostgresQL
- #### HoneySQL
- #### Next.JDBC
- #### Askonomm.Ruuter
- #### Hiccup
- #### Cognitect.Test-Runner

## To run the server on your PC:
1. Install [Clojure](https://clojure.org/guides/getting_started)
2. Install [Babashka](https://book.babashka.org)
3. Clone the [repo](https://github.com/Stansom/passwords_server.git)
4. Open dir with cloned repo in your terminal
```console
cd "/path/to/cloned/repo"
```
4. Run the command to start server and you can specify a port by using '-p' option:
```console
bb -m passman.app -p 8888
```

## Supported APIs
- POST 
    - "/register" - **Register a new user**
    - "/add?url=url&login=resource-login&password=resource-password" - **Adds a new password for resource**
    - "/login" - **Login**
- GET 
    - "/list?username=name&password=password" - **Returns a list with all password for the user**
    - "/test" - **Returns request**
    - "/random" - **Generates random password**
    - "/view/login" - **Login user**
    - "/view/list" - **Display passwords in browser**
    - "/logout" - **Log out**
- PATCH 
    - "/update-url?id=entry-id&newurl=url" - **Updates url**
    - "/update-pass?id=entry-id&newpassword=password" - **Updates password**
    - "/update-login?id=entry-id&newlogin=login" - **Updates login**
- DELETE "/remove-pass?id=password-id" - **Remove password**
