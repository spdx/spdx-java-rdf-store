# Spdx-Java-Rdf-Store

This Java library implements an RDF store supported [SPDX spec version 2.3][spdx2.3] and earlier implementing the [SPDX Java Library Storage Interface][storage] using an underlying RDF store.

[spdx2.3]: https://spdx.github.io/spdx-spec/v2.3/
[storage]: https://github.com/spdx/Spdx-Java-Library#storage-interface

## Code quality badges

[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=spdx-rdf-store&metric=bugs)](https://sonarcloud.io/dashboard?id=spdx-rdf-store)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=spdx-rdf-store&metric=security_rating)](https://sonarcloud.io/dashboard?id=spdx-rdf-store)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=spdx-rdf-store&metric=sqale_rating)](https://sonarcloud.io/dashboard?id=spdx-rdf-store)
[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=spdx-rdf-store&metric=sqale_index)](https://sonarcloud.io/dashboard?id=spdx-rdf-store)

## Using the Library

This library is intended to be used in conjunction with the [SPDX Java Library](https://github.com/spdx/Spdx-Java-Library).

Simply create a new instance of `RdfStore(documentNamespace)` and reference it as your storage.  The documentNamespace is the namespace used for the store.

Note that this version of the RDF library only supports a single document namespace and DOES NOT support [SPDX spec versions 3.0][spdx3.0] and later.

[spdx3.0]: https://spdx.github.io/spdx-spec/v3.0/

## Serializing and Deserializing RDF Formats

This library supports the [`ISerializableModelStore`][ISerializableModelStore] interface for serializing and deserializing RDF files and data stores.

The format is specified by calling the `setOutputFormat(OutputFormat outputFormat)` method.

OutputFormat must be one of RDF/XML-ABBREV (default), RDF/XML, N-TRIPLET, or TURTLE.

A convenience method `public String loadModelFromFile(String fileNameOrUrl, boolean overwrite)` can be used to load the model from a file or URL.

[ISerializableModelStore]: https://spdx.github.io/spdx-java-core/org/spdx/storage/ISerializableModelStore.html

## Development Status

Less stable - it has just been updated to support the redesigned storage interfaces.
