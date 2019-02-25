# TODO

- make the tagless algebra reflect the whole language syntax

- implement transformations:

    - predicate bindings moved into the _where_ section 
    - predicate match conditions moved into the _if_ section
    - references to predicate values replaced with product member operations
    - atoms replaced with variables 

- typeclasses defined for the elements of a product, to be implemented for each product type that represents an event or aggregation
- drop Opaque, Func and ConsProd

- predicate symbols and parafact symbols associated with types

- generated rule = base rule * atom variable binding

- master table of atom -> rule * variable

- evaluation of guard yields true, false, maybe with tri-state logic.  maybe = unbound variables

- inner loop:

    - receive event type E
    - look up atoms type E (hashed and unhashed)
       - check rule expiration
       - bind atom variable to event and evaluate rule guard
          - true: evaluate rule head
          - false: ignore
          - maybe: generate new rule

- define a hash typeclass to be implimented by each event type

