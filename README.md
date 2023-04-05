# datomic-query

Practical examples on how to query data from Datomic.

* Find Specifications
* Inputs
* Not Clauses
* Function Expression
* Pull API
* Reverse Lookup
* History API
* Entity API
* BYO data

Check the `test/datomic_query/all_test.clj`.

## Database

In Datomic, a database comprised of a set of datoms.

## Datoms

An immutable atomic fact that represents the addition or retraction of a
relation between an entity, an attribute, a value, and a transaction.

A datom is expressed as a five-tuple:

* an entity id (E)
* an attribute (A)
* a value for the attribute (V)
* a transaction id (Tx)
* a boolean (Op) indicating whether the datom is being added or retracted

## Example Datom

    E	42
    A	:user/favorite-color
    V	:blue
    Tx	1234
    Op	true

## Entities

An entity is a set of datoms that are all about the same E.

## Example Entity

An entity can be visualized as a table:

    E	A	                    V	    Tx	    Op
    42	:user/favorite-color	:blue	1234	true
    42	:user/first-name	    "John"	1234	true
    42	:user/last-name	        "Doe"	1234	true
    42	:user/favorite-color	:green	4567	true
    42	:user/favorite-color	:blue	4567	false
