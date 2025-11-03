package com.thebrchub.demo.app.resource;

import java.util.Locale;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import com.stripe.Stripe;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import com.thebrchub.demo.app.config.BaseLogger;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@PermitAll
@Path("/v1/pay")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
public class PaymentResource {

    @ConfigProperty(name = "stripe.api.key")
    private String stripeApiKey;

    @ConfigProperty(name = "stripe.api.currency", defaultValue = "inr")
    private String currency;

    @ConfigProperty(name = "stripe.webhook.scrt")
    private String endpointSecret;

    @ConfigProperty(name = "stripe.base.url")
    private String baseUrl;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeApiKey;
    }

    @Path("/success")
    @GET
    public String paymentSuccess() {
        return "Payment successful!";
    }

    @Path("/cancel")
    @GET
    public String paymentCancelled() {
        return "Payment was cancelled.";
    }

    @POST
    @Path("/checkout")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createCheckoutSession(@Valid @NotNull @Min(100) Long quantity) throws Exception {

        try {
            final String productId = "UUID-1";
            final Long unitAmount = 100L;
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(baseUrl + "/v1/pay/success")
                    .setCancelUrl(baseUrl + "/v1/pay/cancel")
                    .setPaymentIntentData(
                            SessionCreateParams.PaymentIntentData.builder()
                                    .putMetadata("SP-OrderId", productId)
                                    .build())
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setQuantity(quantity)
                                    .setPriceData(
                                            SessionCreateParams.LineItem.PriceData.builder()
                                                    .setCurrency(currency)
                                                    .setUnitAmount(unitAmount)
                                                    .setProductData(
                                                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                    .setName("Demo Product")
                                                                    .build())
                                                    .build())
                                    .build())
                    .build();

            Session session = Session.create(params);

            return Response.ok().entity(session.getId()).build();
        } catch (Exception ex) {
            BaseLogger.LOG.error("checkout processing failed", ex);
            return Response.serverError().entity("checkout processing failed.").build();
        }
    }

    @POST
    @Path("/webhook")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response handleStripeEvent(String payload,
            @HeaderParam("Stripe-Signature") String sigHeader) {

        final Event event;

        try {
            event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
        } catch (Exception e) {
            BaseLogger.LOG.error("Webhook signature verification failed", e);
            return Response.serverError().entity("Invalid webhook signature.").build();
        }

        try {
            switch (event.getType().toLowerCase(Locale.ENGLISH)) {
                case "checkout.session.completed":
                    handleCheckoutSessionCompleted(event);
                    break;

                case "charge.refunded":
                    // handleChargeRefunded(event);
                    break;

                default:
                    BaseLogger.LOG.warn("Unhandled Stripe event type: " + event.getType());
            }

            return Response.ok().entity("Webhook received for event : " + event.getType()).build();
        } catch (Exception ex) {
            BaseLogger.LOG.error("Webhook processing failed", ex);
            return Response.serverError().entity("Webhook processing failed.").build();
        }
    }

    private void handleCheckoutSessionCompleted(Event event) throws Exception {
        Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
        if (session == null) {
            BaseLogger.LOG.error("Session data missing in checkout.session.completed event.");
            return;
        }

        String paymentIntentId = session.getPaymentIntent();
        PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);

        if (!"succeeded".equals(paymentIntent.getStatus())) {
            BaseLogger.LOG.warn("Payment not successful. Status: " + paymentIntent.getStatus());
            return;
        }

        // Map<String, String> metadata = paymentIntent.getMetadata();
        // Long orderId = Long.parseLong(metadata.get("HMS-OrderId"));

        // try {
        // commonUtil.confirmOrder(orderId);
        // } catch (Exception e) {
        // BaseLogger.LOG.error("Post-payment processing failed. Initiating refund for
        // orderId: " + orderId, e);
        // handleOrderFailure(paymentIntent, orderId);
        // }
    }

    // private void handleChargeRefunded(Event event) {
    // try {
    // Charge charge = (Charge)
    // event.getDataObjectDeserializer().getObject().orElse(null);
    // if (charge == null) {
    // BaseLogger.LOG.error("Missing charge object in charge.refunded event.");
    // return;
    // }

    // Map<String, String> metadata = charge.getMetadata();
    // if (!metadata.containsKey("HMS-OrderId")) {
    // BaseLogger.LOG.error("Refund Metadata does not contain HMS-OrderId : Complete
    // MetaData : " + metadata);
    // return;
    // }

    // Long orderId = Long.parseLong(metadata.get("HMS-OrderId"));
    // commonUtil.updateOrderPaymentStatus(orderId, PaymentStatus.REFUND_COMPLETED);

    // } catch (Exception e) {
    // BaseLogger.LOG.error("Error handling charge.refunded event", e);
    // }
    // }
}