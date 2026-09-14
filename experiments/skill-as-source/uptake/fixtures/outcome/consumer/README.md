# user-directory

Looks users up in a remote directory through `UserSource`.

Every lookup returns an `Outcome` from `com.example.acme:acme-result`, a sealed type that is either `Success` with the value or `Failure` with the error. `UserCache` shows the pattern the project uses for it.
