package com.quorumtrading.adapter.payment.stripe;

import com.stripe.model.Address;
import com.stripe.model.Charge;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.LineItem;
import com.stripe.model.LineItemCollection;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Price;
import com.stripe.model.Product;
import com.stripe.model.Refund;
import com.stripe.model.StripeCollection;
import com.stripe.model.StripeError;
import com.stripe.model.StripeObject;
import com.stripe.model.StripeRawJsonObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.ApiResource;
import com.stripe.param.checkout.SessionCreateParams;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Registers Stripe SDK classes for GraalVM native image reflection.
 * <p>
 * The Stripe Java SDK uses Gson for JSON serialization/deserialization.
 * Gson relies on reflection, which must be explicitly registered for
 * GraalVM native images.
 * <p>
 * Uses {@code targets} with {@code ignoreNested = false} (default) so that
 * all nested/inner classes of each target are automatically registered.
 * This is critical for {@link SessionCreateParams} which has 296+ inner
 * builder classes that Gson needs to serialize via reflection.
 */
@RegisterForReflection(targets = {
    // === Checkout session params (request serialization) ===
    SessionCreateParams.class,

    // === Checkout session response (deserialization) ===
    Session.class,

    // === Event / Webhook ===
    Event.class,

    // === Payment Intent (webhook events) ===
    PaymentIntent.class,

    // === Common model types used in responses ===
    StripeObject.class,
    StripeRawJsonObject.class,
    StripeCollection.class,
    StripeError.class,
    LineItem.class,
    LineItemCollection.class,
    Price.class,
    Product.class,
    Address.class,
    Customer.class,
    Charge.class,
    Refund.class,

    // === Stripe SDK core ===
    ApiResource.class,
}, classNames = {
    // Event internals (not directly importable in SDK 28.x)
    "com.stripe.model.EventData",
    "com.stripe.model.EventDataObjectDeserializer",

    // Stripe interfaces (needed for Gson type resolution)
    "com.stripe.model.HasId",
    "com.stripe.model.MetadataStore",
    "com.stripe.net.StripeResponse",
    "com.stripe.net.StripeResponseGetter",

    // Gson internals used by Stripe SDK for JSON serialization
    "com.google.gson.internal.LinkedTreeMap",
    "com.google.gson.internal.LinkedTreeMap$Node",
    "com.google.gson.internal.$Gson$Types$ParameterizedTypeImpl",
})
public class StripeReflectionConfig {
}
