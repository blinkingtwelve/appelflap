# The problem

GeckoView's cache administration is stored in SQLite databases, one per page origin.
For Appelflap to produce a redistributable cache bundle, it needs read access to this database. And to insert/update a cache, it needs write access.
SQLite locks are not per row or per table, but per database file — see [the SQLite documentation](https://www.sqlite.org/lockingv3.html) for details.

Appelflap does not run in the same process as GeckoView, and so cannot share its GeckoView database "connection". 

## Packing up a cache

To pack up a cache, Appelflap will try to acquire a self-consistent snapshot of the metadata database — it does so by using the SQLite backup API. But if the webapp is using any of the caches (whether reading or writing), this won't fly. To make matters worse, GeckoView takes a while to release locks after any operation. I've done some experiments reading and writing from caches in Firefox, and simultaneously trying to acquire a read lock on the affected database in another process, and it looks like it's rather unpredictable / hard to influence for how long the database will remain locked. For instance, reading a Request from the cache and dumping the result into a developer console will keep the database locked indefinitely (which is not great for debugging), suggesting that lifecycle of the resultant JS object is a factor. Writing to a cache seems to lock the database for a long time.

Closing the page which is using the cache releases any lock immediately, though. So, one option would be for Appelflap to close (and later reopen) the webapp's GeckoView session, but that would also interrupt any API requests running in that page context — Appelflap would succeed in copying the database, but will not be able to report back success or failure of the whole operation, as it has killed the caller. Also, upon recreating the session, it may not be possible to restore the page exactly as it was before, so likely the user will notice the interruption (however brief).

There is another way, though: copy the database (base file and WAL) through the filesystem. This avoids any SQLite locking. However, there are concerns with this approach:

0. Uncommitted transactions will be rolled back. That's an acceptable risk. When the webapp puts in its PUT for a Cache publication, it is assumed it's aware that messing around with the cache in re will give unpredictable results in terms of what exactly will be packed up.
1. Data smear - as we're copying a file that another process is writing into, we may have read stale data that has become inconsistent with what we still have to read (if the other process modifies data at earlier position than our file pointer).
Luckily, this smearing effect can be detected — in rough terms, the algorithm for acquiring a snapshot would be to keep perform the copying until copy iteration `N` gives the exact same result as copy iteration `N+1`.

Taking these considerations into account, Appelflap will try to create a "nice" backup first, and trying the desperate iterative-copy-until-stable-result-is-achieved in the desperate case.

## Injecting into a cache

Acquiring an exclusive lock needed for insertions and updates is not easy, it has all of the problems described above for acquiring a shared lock, plus more. But even if Appelflap could predictably succeed in acquiring the exclusive lock, what would happen to GeckoView?
It turns out that when an exclusive lock is acquired, the webapp-side of things will be confronted with JS-exceptions with `NS_ERROR_STORAGE_BUSY` name attribute.
On the one hand, that's nice, as at least GeckoView doesn't crash, and the exception is directly related to the problem - and the webapp could potentially try again. On the other hand, it's unlikely that all parts of the webapp, especially opaque third-party libraries (such as Workbox), will do the right thing when confronted with this exception. With a sufficiently disciplined webapp this aspect of the locking problem could be resolved. Another option would be to send GeckoView a `SIGSTOP` once Appelflap has acquired the read lock, freezing it while its caches are meddled with, unfreezing it once Appelflap finishes up its injections.

However, the problem of acquiring the exclusive lock remains. Candidate strategies I've come up with so far:

0. Only do any outstanding injection work on startup, when Appelflap itself has launched, but before it launches the GeckoView machinery.

1. When Appelflap has injection work to do, it doesn't need to happen right now. It can bide its time, repeatedly trying to acquire an exclusive lock on the database, until it succeeds, and as soon as it does, it'll `SIGSTOP` the Geckoview process, perform the injections, and `SIGCONT`-ing it when the lock is released, thereby minimizing opportunity for JS-exceptions.

2. Shut down GeckoView, and restart it when done. One advantage is that the cache database will not need to be modified in-place — Appeflap could stage the operations on a copy, and only if things went well, move it over the original database. However, the intervention will likely be noticed by an active user — it depends a bit on what they're doing. If they're just browsing a page, chances are that the Gecko session will be restored as it was, and if the user was looking up to the blackboard for a second or so they might not even notice that their webapp flashed at them. But if they're watching a video, things are different. One way to alleviate this problem would be to erect an API so that the webapp can let Appelflap know when it would be a really bad (or really good) time to perform injections. 

For a first pass, I'm leaning towards option 0.

