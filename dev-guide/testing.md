---
layout: default
title: Testing with Dagger
redirect_from:
  - /testing
---

One of the benefits of using dependency injection frameworks like Dagger is that
it makes testing your code easier. This document explores some strategies for
testing applications built with Dagger.

## Replace bindings for functional/integration/end-to-end testing

Functional/integration/end-to-end tests typically use the production
application, but substitute [fakes][fakes] (don’t use mocks in large functional
tests!) for persistence, backends, and auth systems, leaving the rest of the
application to operate normally. That approach lends itself to having one (or
maybe a small finite number) of test configurations, where the test
configuration replaces some of the bindings in the prod configuration.

<a name="separate-component-configurations"></a>

### Separate component configurations

The recommended approach is to have a separate component configuration for each
environment (e.g. production and testing). The testing component type extends
the production component type so that it gets all of the same entry point and
corresponding interfaces, but it installs a different set of modules.

```java
@Component(modules = {
  OAuthModule.class, // real auth
  FooServiceModule.class, // real backend
  OtherApplicationModule.class,
  /* … */ })
interface ProductionComponent {
  Server server();
}

@Component(modules = {
  FakeAuthModule.class, // fake auth
  FakeFooServiceModule.class, // fake backend
  OtherApplicationModule.class,
  /* … */})
interface TestComponent extends ProductionComponent {
  FakeAuthManager fakeAuthManager();
  FakeFooService fakeFooService();
}
```

Now the main method for your test binary calls `DaggerTestComponent.builder()`
instead of `DaggerProductionComponent.builder()`. Note that the test component
interface can add provision handles to the fake instances (`fakeAuthManager()`
and `fakeFooService()`) so that the test can access them to control the harness
if necessary.

### Do not override bindings by subclassing modules

You might think a simple way to replace bindings in a testing component is to
override modules’ `@Provides` methods in a subclass. Then, when you create an
instance of your Dagger component, you can pass in instances of the modules it
uses. (You do not have to pass instances for modules with no-arg constructors or
those without instance methods, but you can.) For example:

```java
@Component(modules = {AuthModule.class, /* … */})
interface MyApplicationComponent { /* … */ }

@Module
class AuthModule {
  @Provides AuthManager authManager(AuthManagerImpl impl) {
    return impl;
  }
}

class FakeAuthModule extends AuthModule {
  @Override
  AuthManager authManager(AuthManagerImpl impl) {
    return new FakeAuthManager();
  }
}

MyApplicationComponent testingComponent = DaggerMyApplicationComponent.builder()
    .authModule(new FakeAuthModule())
    .build();
```

<a name="do-not-override"></a> But there are problems with this approach:

*   Using a module subclass cannot change the static shape of the binding graph:
    it cannot add or remove bindings, or change bindings’ dependencies.
    Specifically:

    *   Overriding a `@Provides` method can’t change its parameter types, and
        narrowing the return type has no effect on the binding graph as Dagger
        understands it. In the example above, `testingComponent` still requires
        a binding for `AuthManagerImpl` *and all its dependencies*, even though
        they are not used.

    *   Similarly, the overriding module cannot add bindings to the graph,
        including new [multibinding](multibindings.md) contributions (although
        you can still override a `SET_VALUES` method to return a different set).
        Any new `@Provides` methods in the subclass are silently ignored by
        Dagger. Practically, this means that your fakes cannot take advantage of
        dependency injection.

*   `@Provides` methods that are overridable in this way cannot be static, so
    their module instances cannot be [elided][elide-static-module-instances].
    This will affect runtime performance of your production code as well.

*   This method is brittle and usually makes code changes difficult. Most users
    won't expect the `@Provides` methods to be overridden by a test, and so
    adding new dependencies will break tests even when it would be a functional
    no-op in Dagger.

## Organize modules for testability

Module classes are a kind of [utility class]: a collection of
independent `@Provides` methods, each of which may be used by the
injector to provide some type used by the application.

<!-- TODO(dpb): Reformat as a footnote once Github supports them. -->
(Although several `@Provides` methods may be related in that one depends on
a type provided by another, they typically do not explicitly call each
other or rely on the same mutable state. Some `@Provides` methods do refer
to the same instance field, in which case they are not in fact independent.
The advice given here treats `@Provides` methods as utility methods anyway
because it leads to modules that can be readily substituted for testing.)

So how do you decide which `@Provides` methods should go together into one
module class?

One way to think about it is to classify bindings into *published* bindings and
*internal* bindings, and then to further decide which of the published bindings
has reasonable alternatives.

**Published** bindings are those that provide functionality that is used by
other parts of the application. Types like `AuthManager` or `User` or
`DocDatabase` are published: they are bound in a module so that the rest of the
application can use them.

**Internal** bindings are the rest: bindings that are used in the
implementation of some published type and that are not meant to be used except
as part of it. For example, the bindings for the configuration for the OAuth
client ID or the `OAuthKeyStore` are intended to be used only by the OAuth
implementation of `AuthManager`, and not by the rest of the application. These
bindings are usually for package-private types or are qualified with package-
private qualifiers.

<!-- TODO(dpb): Explicit examples of making internal types "private". -->

Some published bindings will have reasonable alternatives, especially for
testing, and others will not. For example, there are likely to be alternative
bindings for a type like `AuthManager`: one for testing, others for different
authentication/authorization protocols.

But on the other hand, if the `AuthManager` interface has a method that returns
the currently logged-in user, you might want to publish a binding that provides
`User` by simply calling `getCurrentUser()` on the `AuthManager`. That
published binding is unlikely to ever need an alternative.

Once you’ve classified your bindings into published bindings with reasonable
alternatives, published bindings without reasonable alternatives, and internal
bindings, consider arranging them into modules like this:

*   One module for each published binding with a reasonable alternative. (If you
    are also writing the alternatives, each one gets its own module.) That
    module contains exactly one published binding, as well as all of the
    internal bindings that that published binding requires.

*   All published bindings with no reasonable alternatives go into modules
    organized along functional lines.

*   The published-binding modules should each include the
    no-reasonable-alternative modules that require the public bindings each
    provides.

It’s a good idea to document each module by describing the published bindings it
provides.

Here’s an example using the auth domain. If there is an `AuthManager` interface,
it might have an OAuth implementation and a fake implementation for testing. As
above, there might be an obvious binding for the current user that you wouldn’t
expect to change among configurations.

```java
/**
 * Provides auth bindings that will not change in different auth configurations,
 * such as the current user.
 */
@Module
class AuthModule {
  @Provides static User currentUser(AuthManager authManager) {
    return authManager.currentUser();
  }
  // Other bindings that don’t differ among AuthManager implementations.
}

/** Provides a {@link AuthManager} that uses OAuth. */
@Module(includes = AuthModule.class) // Include no-alternative bindings.
class OAuthModule {
  @Provides static AuthManager authManager(OAuthManager authManager) {
    return authManager;
  }
  // Other bindings used only by OAuthManager.
}

/** Provides a fake {@link AuthManager} for testing. */
@Module(includes = AuthModule.class) // Include no-alternative bindings.
class FakeAuthModule {
  @Provides static AuthManager authManager(FakeAuthManager authManager) {
    return authManager;
  }
  // Other bindings used only by FakeAuthManager.
}
```

Then your production configuration will use the real modules, and the testing
configuration the fake modules, as described
[above](#separate-component-configurations).

## Unit tests and manual instantiation [unit tests]

For smaller unit tests, it might seem like a good idea to just avoid using
Dagger entirely and just instantiate objects by calling the `@Inject`
constructor. However, there are downsides to this approach. See this
[testing philosophy] discussion for those downsides.

[Dagger docs]:https://dagger.dev/hilt/testing-philosophy#manual-instantiation

It is generally recommended to create a Dagger component in your tests to
instantiate objects, whether that is a larger test component for many tests or a
small focused test component for the individual test.

<!-- References -->

[cohesive]: https://en.wikipedia.org/wiki/Cohesion_(computer_science)
[fakes]: http://googletesting.blogspot.com/2013/07/testing-on-toilet-know-your-test-doubles.html
[single responsibility principle]: https://en.wikipedia.org/wiki/Single_responsibility_principle
[unit tests]: https://en.wikipedia.org/wiki/Unit_testing
[utility class]: https://en.wikipedia.org/wiki/Utility_class

<!-- TODO(gak): Document the elision of static module instances somewhere externally. -->
[elide-static-module-instances]: https://github.com/google/dagger/commit/9b81e1091d5e9ddd1e84318fbab863a8c62fb757
