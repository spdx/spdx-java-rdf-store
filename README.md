# Spdx-Java-Rdf-Store

This Java library implements an RDF store implementing the [SPDX Java Library Storage Interface](https://github.com/spdx/Spdx-Java-Library#storage-interface) using an underlying RDF store.

# Using the Library

This library is intended to be used in conjunction with the [SPDX Java Library](https://github.com/spdx/Spdx-Java-Library).

Simply create a new instance of `RdfStore()` and reference it as your storage.

# Serializing and Deserializing RDF Formats

This library supports the `ISerializableModelStore` interface for serializing and deserializing RDF files and data stores.

The format is specified by calling the `setOutputFormat(OutputFormat outputFormat)` method.

OutputFormat must be one of RDF/XML-ABBREV (default), RDF/XML, N-TRIPLET, or TURTLE.

A convenience method `public String loadModelFromFile(String fileNameOrUrl, boolean overwrite)` can be used to load the model from a file or URL.

# Development Status

Mostly stable - although it has not been widely used.

