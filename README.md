# What is this?

The aim here is to develop a version of the datalog language for programs that continuously react to events in the environment and generate effects on the environment.  

# Isn't that Prolog?

It is not an aim to reinvent prolog. We want a purely declarative language that is not in itself Turing complete.

# Why?

It will be a better way to do realtime event processing and some forms of signal processing. 

More [here](language.md).

# Begining of an Implementation

Starting with a typed relational algebra augmented with recursion. (This may be a nice way to program the system in itself.)  Datalog will come later, probably on top of this.

At this point the algebra, represented as a GADT, is compiled in two passes to a function Queue => Queue and the runtime finds the fixpoint of this.  

But, actually, the Queue type is mutable and an effect Queue => Unit is is iterated but that is a detail. [Mutable Queue](queue.md)






