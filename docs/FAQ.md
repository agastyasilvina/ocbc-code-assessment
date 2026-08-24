# FAQ

Questions go to your vendor manager. Answers appear here, on this repository's `main`, so every candidate gets the same answer. Fetch `upstream` now and then.

**Can I use Spring MVC, Kotlin, another database or another build tool?**
No. The rules in the README apply to everyone. Java 21, Spring WebFlux with Reactor, R2DBC with Postgres, Maven.

**My fork is public. Can other candidates see my work?**
Yes. This repository is public, and so is every fork of it. Copying doesn't help: we compare all submissions with each other, and shortlisted candidates extend their own solution live. Never commit anything secret to your fork.

**Can I change the mock bank or the web client to make testing easier?**
No. Use the mock bank's admin API (`docs/integration/mock-bank-admin.md`) to set up the situation you need. We run your service against our own copies of both.

**The code in this repository doesn't follow `docs/api.md`. Which one is right?**
`docs/api.md`. It is the agreed contract; the implementation drifted from it.

**Something in this repository looks broken, but it's not in the incident list. Should I fix it?**
Yes. We know of more problems than the incidents list. Finding and fixing them counts. Say what you found in `DESIGN.md`.
