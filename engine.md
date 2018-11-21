# Preface: What is Datalog?

[Datalog](https://en.wikipedia.org/wiki/Datalog) is a declarative programming language based on first order predicate logic.  It is not turing complete and datalog programs are guaranteed to terminate.  

The following example introduces the basic terminology.  (If you are familiar, skip this section.)

A program consists of _rules_ such as these:

```
ancestor(X, Y) :- parent(X, Y).
ancestor(X, Y) :- parent(X, Z), ancestor(Z, Y).
```

A program operates on _facts_ such as:

```
parent(bill, mary).
parent(mary, john).
```

Applying rules to facts produces derived facts. In this example, the fact  `ancestor(bill, john)` can be derived.

Take the second rule above: `ancestor(X, Y) :- parent(X, Z), ancestor(Z, Y)`. It has these parts:

* The term `ancestor(X, Y)` to the left of the `:-` is the _head_ of the rule.
* The expression `parent(X, Z), ancestor(Z, Y)` on the right is the _formula_.
* In the formula, each term `parent(X, Z)` and `ancestor(Z, Y)` is an _atom_.
* The identifiers `ancestor` and `parent` are _predicates_.
* The identifiers `X`, `Y` and `Z` are _variables_ which stand for values.

A rule is read as an implication: if the predicates in the formula are true then the predicate in the head is true for a given set of bindings of the variables to values.

A predicate with two arguments such as `parent(X, Y)` can be viewed an association between individuals. A predicate of one argument, for example `male(X)`, defines a class of individuals.  

More generally, a predicate can have one or more arguments and can be viewed as a _relation_. 

Any relational algebra expression can be written in datalog. Conversely, a recursive datalog rule such as the rule for `ancestor` cannot be expressed in (basic) relational algebra.  

# Batch or Continuous

Datalog programs are often conceived as queries.  A predicate is marked as the _goal_. The facts matching the goal are computed from facts in a database and the program terminates.  This is essentially a one-shot or batch operation.

The aim here is to develop a version of datalog that continuously reacts to facts as they are observed in the environment.  The goals are replaced by actions applied to the environment.

One challenge is that datalog is a purely delarative language. Facts may be derived in any order or any number of times from the same input.  This is addressed by making the concept of time explicit in facts and rules. 

# The Time Dimension

Starting with standard datalog concepts we add specializations for time and events:

  - An _event_ is a fact with a timestamp.  Every fact in this system is an event, either a supplied _input event_ or a _derived event_, derived via a rule.

  - In a rule, timestamps can be bound using a special syntax or omitted.  When omitted in a formula, the timestamp is free. When omitted from a rule head, the timestamp is a function of the timestamps of the events matching the formula. 

  - An _action_ is a query or goal with an associated side effect. The side effect is executed exactly once for each distinct event matching the goal. 
  
  - Input events are processed incrementally in approximate time order with a specified maximum timestamp skew. 

In effect, a new solution is computed at each iteration considering all input events with `timestamp < t - skew` and no events with `timestamp > t`. The side effect for an event matching an action is executed in the first iteration deriving that event.

# Evaluation Sketch

Naive, bottom-up evaluation is illustrated, which allows events to be processed incrementally. An event `e` with timestamp `t` is written `e@t`.  The evaluation strategy also associates a timestamp with each rule `r`, written as `r@t`.

- Event `e0@t0` arrives
- For `a` in atoms matching event:
    - For `r@t` in rules containing `a`
    - Compute `r1@t1` from `r@t`
        - Remove `a` from formula
        - Substitute variables with respective bindings
        - Let `t1 = max(t0, t)`
    - If `r1.formula` is empty 
        - If the guard* evaluates true emit `r1.head` as event `e1@t1`
    - Else add `r1@t1` to the corpus

## Guard*

  - A _guard_ is a condition that may be attached to a rule. A guard expression may reference variables but not bind them (unlike atoms). 

# Expiration

The stream of events is unbounded and, if the rules were applied to the entire history of events, that would require unbounded time and space.  This can be seen the in last step of the sketch above where new rules, `r1@t1`, are continually added to the corpus but never removed.  

In mitigation, the concept of expiration is introduced.

Each event has a expiration time, `u`, with the intention that an event `e@time(t)@expires(u)` is applicable in the time interval (`t`,  `u`].  

To enforce this, event expiration times are propagated to newly generated rules as  conditions.  

Consider a rule`r0@time(t0)@expires(u0)` that is matched by an event `e@time(t)@expires(u)` producing a new rule `r1@time(t1)@expires(u1)`. Then:

1. `u1 = min(u, u0)` and `t1 = max(t, t0)` (expiration and timestamp propagation)
2. `t < u0` (expiration testing)

If `t >= u0 + skew` the rule can be removed entirely. The timestamp of every following event, `tn` is constrained by `tn + skew >= t`. Therefore `tn >= u0` and the expiration test (2) fails.

# Timestamps as Parafacts

The timestamp and expiration values attached to events are special cases of a concept introduced here: the _parafact_.  Any semilattice type can be used as a parafact. That is, a partially ordered set with an idempotent, commutative, associative `merge` operation. 

Examples:

  - timestamps with with `merge` as maximum 
  - expiration times with with `merge` as minimum 
  - provenance as a set of sources { s1, ... } and `merge` as set union.
  - products of the above: (min timestamp, max timestamp, provenance)

Syntactically, predicates prefixed with `@` denote parafacts.  For example `@time(t)`. 

Parafacts are described in detail [here](latticework.md).

# Aggregation

Aggregations may be defined over the solutions of a formula as parafacts.  For example:  

```
p1(v1, v2) @average(v3) @max(v3) := p2(v1, v2, v3, v4) ;
```

This rule produces a `p1` event for each distinct pair, `v1` and `v2` found in `p2` events. Aggregates of `v3` are computed over the group of `p2` events deriving a specific `p1` event.

TBD: lattice type for computing averages.

# Time Series

Some events report state changes, for example `water_level(tank1, 25.0)@time(t1)` and `water_level(tank1, 27.0)@time(t2)` report changes in a water level over time.  

Assuming `x` and `y` are free, the following are valid atoms:

- `water_level(tank1, x)` has no timestamp predicate and matches each event in the water level time series, binding the level to `x`. 

- `water_level(tank1, x) @ time(y)` also matches each event in the series and binds the  time of the event to `y`.

- `water_level(tank1, x) @ time(t)` matches the event with the given time `t`.

Aggregations can be applied to time series. For example this accumulates and average level for each tank, `x`:

```
average_water_level(x) @ average(y) := water_level(x, y)
```

# Clocks and Resampling

The foregoing timestamp predicates can be used to with _clock_ events to resample a time series.

The atom `clock(offset, period)@time(y)` matches a clock event every `period` milliseconds and binds the time to a free variable `y`.  The first of these events has timestamp `offset`. Neither `period` nor `offset` may be free. 

```
water_level_smoothed(tank1) @average(x) @time(tc-period/2) := 
  water_level(tank1, x)@time(te) ^ clock(offset, period)@time(tc)
  if te < tc ^ te >= tc-period
```

# Syntax

```bnf
Expr        ::= Literal 
              | Variable 
              | '_'
              | Application 
              | '(' Expr ')'

Application ::= Name '(' Expr { ',' Expr } ')'
              | Expr Operator Expr

Pattern     ::= Application
              | '(' Pattern ')'

Atom        ::= Pattern { '@' Pattern | ~ Name }

Guard       ::= Expr { ^ Expr }

Formula     ::= Atom { '^' Atom } [ 'if' Guard ]

Def         ::= Variable '=' Expr

Defs        ::= Def { ',' Def }

Rule        ::= Atom [ ':=' Formula ] [ 'where' Defs ] ';'

Corpus      ::= { Rule }
```
