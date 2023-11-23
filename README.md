# Pie à la mode

In _The Pragmatic Programmer_ in the chapter on concurrency, there's an example
to demonstrate some of the issues with concurrency.

> You’re in your favorite diner. You finish your main course, and ask your
> server if there’s any apple pie left. He looks over his shoulder, sees one piece
> in the display case, and says yes. You order it and sigh contentedly.
> Meanwhile, on the other side of the restaurant, another customer asks their
> server the same question. She also looks, confirms there’s a piece, and that
> customer orders.
>
> One of the customers is going to be disappointed.
>
> Swap the display case for a joint bank account, and turn the waitstaff into
> point-of-sale devices. You and your partner both decide to buy a new phone
> at the same time, but there’s only enough in the account for one. Some-
> one—the bank, the store, or you—is going to be very unhappy.

This turns into a running example, talking about various ways of approaching
this. Further down they add "pie à la mode" to the menu

- pie-a-la-mode-orig

The original exercise, containing a race condition.

- pie-a-la-mode-epidiah-stm
- pie-a-la-mode-johnny-stm

Two versions using `ref`/`alter`/`dosync`

- pie-a-la-mode-swap-vals
- pie-a-la-mode-tonsky-atom
- pie-a-la-mode-thheller-cas

Three different ways to do it with an atom, respectively using `swap-vals!`,
`:validator`, and `compare-and-set!`. The last one is a bit funny, as it
implements a compare-and-set retry loop, similar to how `swap!` over an atom
works internally.

- pie-a-la-mode-mitesh-juc
- pie-a-la-mode-mitesh-juc-promise

Two versions using a java.util.concurrent.BlockingQueue, centralizing the state
management to go through a single head-chef. They differ in how the order is
communicated back to the waiter, using either another queue, or a promise.

- pie-a-la-mode-filipesilva-core-async

Uses core.async channels to represent the inventory.
