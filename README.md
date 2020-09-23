FileSponge
==========

Proxy file requests to multiple consistent mirrors of a single immutable remote source. The requested files will be always online by storing the first file response into a local database.

This library has been created to retrieve a specific file from multiple telegram bots, without needing to know where the file is or which bot has access to it.