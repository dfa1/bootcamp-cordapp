package java_examples;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;

import java.security.PublicKey;
import java.util.List;

// `InitiatingFlow` means that we can start the flow directly (instead of
// solely in response to another flow).
@InitiatingFlow
// `StartableByRPC` means that a node operator can start the flow via RPC.
@StartableByRPC
public class ArtIssueFlowInitiator extends FlowLogic<Void> {
    private final String title;
    private final String artist;
    private final Party appraiser;
    private final Party owner;

    // Flows can take constructor arguments to parameterize the execution of the flow.
    public ArtIssueFlowInitiator(String title, String artist, Party appraiser, Party owner) {
        this.title = title;
        this.artist = artist;
        this.appraiser = appraiser;
        this.owner = owner;
    }

    private final ProgressTracker progressTracker = new ProgressTracker();

    @Suspendable
    @Override
    public Void call() throws FlowException {
        if (!(getOurIdentity().equals(appraiser)))
            throw new IllegalStateException("This flow must be started by the appraiser.");

        // We pick an arbitrary notary from the network map. In practice,
        // it is always preferable to specify the notary to use explicitly.
        Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

        // We build a transaction using a `TransactionBuilder`.
        TransactionBuilder txBuilder = new TransactionBuilder();

        // After creating the `TransactionBuilder`, we must specify which
        // notary it will use.
        txBuilder.setNotary(notary);

        // We add the new ArtState to the transaction.
        // Note that we also specify which contract class to use for verification.
        ArtState ourOutputState = new ArtState(artist, title, appraiser, owner);
        txBuilder.addOutputState(ourOutputState, ArtContract.ID);

        // We add the Issue command to the transaction.
        // Note that we also specific who is required to sign the transaction.
        ArtContract.Commands.Issue commandData = new ArtContract.Commands.Issue();
        List<PublicKey> requiredSigners = ImmutableList.of(appraiser.getOwningKey(), owner.getOwningKey());
        txBuilder.addCommand(commandData, requiredSigners);

        // We check that the transaction builder we've created meets the
        // contracts of the input and output states.
        txBuilder.verify(getServiceHub());

        // We finalise the transaction builder by signing it,
        // converting it into a `SignedTransaction`.
        SignedTransaction partlySignedTx = getServiceHub().signInitialTransaction(txBuilder);

        // We use `CollectSignaturesFlow` to automatically gather a
        // signature from each counterparty. The counterparty will need to
        // call `SignTransactionFlow` to decided whether or not to sign.
        FlowSession ownerSession = initiateFlow(owner);
        SignedTransaction fullySignedTx = subFlow(new CollectSignaturesFlow(partlySignedTx, ImmutableSet.of(ownerSession)));

        // We use `FinalityFlow` to automatically notarise the transaction
        // and have it recorded by all the `participants` of all the
        // transaction's states.
        subFlow(new FinalityFlow(fullySignedTx));

        return null;
    }
}