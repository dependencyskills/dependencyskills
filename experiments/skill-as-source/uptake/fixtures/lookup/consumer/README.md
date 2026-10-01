# user-directory

Looks users up through a `Lookup<User>` from `com.example.acme:acme-lookup`.

A lookup returns a standard Kotlin `Result`. When no user has the id, the result fails with `NotFound`. `ProfileCache` shows how the project handles that.
