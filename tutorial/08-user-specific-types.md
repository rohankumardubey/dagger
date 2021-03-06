---
layout: default
title: User-specific types
---

Now that users can log in, let's add some code that is specific to the logged in
user. The following `Database` class keeps track of each user and their account
balance. It can keep track of multiple users and different balances.

```java
class Database {
  private final Map<String, Account> accounts = new HashMap<>();

  @Inject
  Database() {}

  Account getAccount(String username) {
    return accounts.computeIfAbsent(username, Account::new);
  }

  static final class Account {
    private final String username;
    private BigDecimal balance = BigDecimal.ZERO;

    Account(String username) {
      this.username = username;
    }

    String username() {
      return username;
    }

    BigDecimal balance() {
      return balance;
    }
  }
}
```

Now let's incorporate the database into `LoginCommand` to print the current
balance when a user logs in:

```java
final class LoginCommand extends SingleArgCommand {
  ...

  @Inject
  LoginCommand(Database database, Outputter outputter) { … }

  @Override
  public Status handleArg(String username) {
    Account account = database.getAccount(username);
    outputter.output(
        username + " is logged in with balance: " + account.balance());
    return Status.HANDLED;
  }
}
```

Try logging in with your name, and then again with `jesse`. Ignore for now that
you can run `login` multiple times in a row without logging out. We'll address
that later.

<section style="text-align: center" markdown="1">

[Previous](07-two-for-the-price-of-one) · [Next](09-maintaining-state)

</section>
