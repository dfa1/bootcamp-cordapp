package java_examples;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.flows.*;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.utilities.ProgressTracker;

// `IAmAFlowResponder` is our second flow, and will communicate with
// `IAmAFlowInitiator`.
// We mark `IAmAFlowResponder` as an `InitiatedByFlow`, meaning that it
// is instantiated automatically in response to a message from its initiating
// flow (`IAmAFlowInitiator` in this case).
@InitiatedBy(IAmAFlowInitiator.class)
public class IAmAFlowResponder extends FlowLogic<Void> {
    private final FlowSession counterpartySession;

    // Responder flows always have a single constructor argument - a
    // `FlowSession` with the counterparty who initiated the flow.
    public IAmAFlowResponder(FlowSession counterpartySession) {
        this.counterpartySession = counterpartySession;
    }

    private final ProgressTracker progressTracker = new ProgressTracker();

    @Suspendable
    @Override
    public Void call() throws FlowException {
        /*-----------------------------------------
         * RESPONDING TO COLLECT_SIGNATURES_FLOW *
        -----------------------------------------*/
        // If the counterparty requests our signature on a transaction using
        // `CollectSignaturesFlow`, we need to respond by invoking our own
        // `SignTransactionFlow` subclass.
        class SignTxFlow extends SignTransactionFlow {
            private SignTxFlow(FlowSession otherSession, ProgressTracker progressTracker) {
                super(otherSession, progressTracker);
            }

            // As part of `SignTransactionFlow`, the contracts of the
            // transaction's input and output states are run automatically.
            // Inside `checkTransaction`, we define our own additional logic
            // for checking the received transaction before signing it.
            @Override
            protected void checkTransaction(SignedTransaction stx) throws FlowException {
                IAmAState outputState = (IAmAState) stx.getTx().getOutputs().get(0).getData();
                if (!(outputState.getFirstAttribute().equals("expected data")))
                    throw new FlowException("Output did not have the expected data.");
            }
        }

        subFlow(new SignTxFlow(counterpartySession, SignTransactionFlow.tracker()));

        /*------------------------------
         * FINALISING THE TRANSACTION *
        ------------------------------*/
        // Nothing to do here! As long as some other party calls
        // `FinalityFlow`, the recording of the transaction on our node
        // we be handled automatically.

        return null;
    }
}