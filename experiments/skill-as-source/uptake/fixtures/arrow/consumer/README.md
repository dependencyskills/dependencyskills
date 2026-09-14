# shop

A small checkout built on Arrow. Every operation that can fail returns an `Either<OrderError, T>`: `Left` with the error or `Right` with the value. `Receipts` shows how the project works with them.
