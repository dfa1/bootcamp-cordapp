package java_examples;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.flows.*;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.utilities.ProgressTracker;

import static net.corda.core.contracts.ContractsDSL.requireThat;

// ``IAmAFlowResponder`` is our second flow, and will communicate with
// ``IAmAFlowInitiator``.
// We mark ``IAmAFlowResponder`` as an ``InitiatedByFlow``, meaning that it
// can only be started in response to a message from its initiating flow.
// That's ``IAmAFlowInitiator`` in this case.
// Each node also has several flow pairs registered by default - see
// ``AbstractNode.installCoreFlows``.
@InitiatedBy(IAmAFlowInitiator.class)
public class IAmAFlowResponder extends FlowLogic<Void> {
    private final FlowSession counterpartySession;

    public IAmAFlowResponder(FlowSession counterpartySession) {
        this.counterpartySession = counterpartySession;
    }

    private final ProgressTracker progressTracker = new ProgressTracker();

    @Suspendable
    @Override
    public Void call() throws FlowException {
        // The ``ResponderFlow` has all the same APIs available. It looks
        // up network information, sends and receives data, and constructs
        // transactions in exactly the same way.

        /*------------------------------
         * SENDING AND RECEIVING DATA *
         -----------------------------*/

        // We need to respond to the messages sent by the initiator:
        // 1. They sent us an ``Object`` instance
        // 2. They waited to receive an ``Integer`` instance back
        // 3. They sent a ``String`` instance and waited to receive a
        //    ``Boolean`` instance back
        // Our side of the flow must mirror these calls.
        Object obj = counterpartySession.receive(Object.class).unwrap(data -> data);
        String string = counterpartySession.sendAndReceive(String.class, 99).unwrap(data -> data);
        counterpartySession.send(true);

        /*-----------------------------------------
         * RESPONDING TO COLLECT_SIGNATURES_FLOW *
        -----------------------------------------*/

        // The responder will often need to respond to a call to
        // ``CollectSignaturesFlow``. It does so my invoking its own
        // ``SignTransactionFlow`` subclass.
        class SignTxFlow extends SignTransactionFlow {
            private SignTxFlow(FlowSession otherSession, ProgressTracker progressTracker) {
                super(otherSession, progressTracker);
            }

            @Override
            protected void checkTransaction(SignedTransaction stx) {
                requireThat(require -> {
                    // Any additional checking we see fit...
                    IAmAState outputState = (IAmAState) stx.getTx().getOutputs().get(0).getData();
                    assert (outputState.getFirstAttribute().equals("new data"));
                    return null;
                });
            }
        }

        subFlow(new SignTxFlow(counterpartySession, SignTransactionFlow.tracker()));

        /*------------------------------
         * FINALISING THE TRANSACTION *
        ------------------------------*/

        // Nothing to do here! As long as some other party calls
        // ``FinalityFlow``, the recording of the transaction on our node
        // we be handled automatically.

        return null;
    }
}