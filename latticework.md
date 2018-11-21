# Parafacts

Starting with standard datalog concepts of facts and rules this system adds _parafacts_.  A set of parafacts is attached to each fact and rule. 

# Purpose

A parafact may represent:

- Timestamp. The time of the event represented by the fact.

- Expiration. A time after which the fact is no longer relevant. 

- Provenance. The original sources of the information represented by the fact.

- Aggregate.  A quantity aggregated from the antecedents of the fact.

- Proof. The ground facts and rules applied to derive a fact. 

# Structure

A parafact, like a fact, is a tuple belonging to a predicate.  

However, each parafact predicate defines a partially order and a merge operation. The merge operation is commutative, associative and idempotent.

In other words, a parafact predicate forms a semilattice. 

Each predicate appears at most once in the set of parafacts associated with a fact.  

A rule produces a set of parafacts for each derived fact and its formula may match parafacts associated with antecedent facts. 


# Deriving Parafacts

Derived parafacts are written in the rule head in terms of variables bound in the formula. 

- the parafact value may be an expression involving more than one variable and/or literal.

- a given variable must not appear in both the fact and one of its parafacts. 

- a given parafact predicate can appear in the rule head more than once.  In that case, the occurrences are merged.

# Matching Parafacts

A rule formula can match values or bind variables to values in parafacts.   

A rule that matches parafacts may not be recursive.

# Passing Parafacts

An antecedent parafact whose predicate is not mentioned in the rule head is automatically passed to the derived fact. Parafacts with the same predicate are merged.

A rule can suppress passing of parafacts for given parafact predicates.
