# Basics

Starting with standard datalog concepts we add specializations for time and events:

  - An _event_ is a fact with a timestamp.  Every fact in this system is an event, either a supplied _input event_ or a _derived event_, derived via a rule.

  - In a rule, timestamps can be bound using a special syntax or omitted.  When omitted in a formula, the timestamp is free. When omitted from a rule head, the timestamp is a function of the timestamps of the events matching the formula. 

  - A _guard_ is a condition that may be attached to a rule. A guard expression may reference variables but not bind them (unlike atoms). 

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
        - If the guard evaluates true emit `r1.head` as event `e1@t1`
    - Else add `r1@t1` to the corpus

# Expiration

The stream of events is unbounded and, if the rules were applied to the entire history of events, that would require unbounded time and space.  This can be seen the in last step of the sketch above where new rules, `r1@t1`, are continually added to the corpus but never removed.  

In mitigation, the concept of expiration is introduced.

Each event and rule has a expiration time, `exp`, with the intention that `e@(t, exp)` is applicable in the time interval (`t`,  `exp`].  This is implemented as follows:

If `e@(t, exp)` matches `r@(t, exp)` producing `r1@(t1, exp1)` then `exp1 = min(e.exp, r.exp)` and `t1 = max(e.t, r.t)` or, assuming events arrive in time order, `t1 = e.t`.

Each rule is implicitly extended with the guard `e.t - skew < r.exp` which effectively expires the rule.   If this predicate fails for an event `e1` it will fail for an any subsequent event `e2` because `e1.t - skew < e2.t` for approximately ordered events. The rule can therefore be discarded.

# Timestamps as Parafacts

The timestamp and expiration values in the foregoing are special cases of semilattices. That is, a partially ordered set with an idempotent, commutative, associative `merge` operation. When deriving a new rule, `r1@t1`, from `r@t` and `e0@t0` we can use `t1 = t0 merge t`.

Examples:

  - timestamps with with `merge` as minimum 
  - timestamps with with `merge` as maximum 
  - provenance as a set of sources { s1, ... } and `merge` as union.
  - products of the above: (min timestamp, max timestamp, provenance)

In general we allow any semilattice variable to be attached to a fact or rule.  These are called parafacts and are [defined here](latticework.md).

# Aggregation

Aggregations may be defined over the solutions of a formula as parafacts.  For example:  

```
p1(v1, v2) @average(v3) @max(v3) := p2(v1, v2, v3, v4) ;
```

This rule produces a `p1` event for each distinct pair, `v1` and `v2` found in `p2` events. Aggregates of `v3` are computed over the group of `p2` events deriving a specific `p1` event.

Predicates prefixed with `@` are parafacts.  Variables bound in the formula that appear in parafacts may not appear in the event (and visa versa).

# Time Series

Some events report state changes, for example `water_level(tank1, 25.0)@t1` and `water_level(tank1, 27.0)@t2` report changes in a water level over time.  

Assuming `x` and `y` are free, the following are valid atoms:

- `water_level(tank1, x)` has no timestamp predicate and matches each event in the water level time series. 

- `water_level(tank1, x) @ time(y)` also matches each event in the series and binds the millisecond time of the event to `y`.

- `water_level(tank1, x) @ time(t)` matches the event with the given millisecond time `t`.

Aggregations can be applied to time series, for example:

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
