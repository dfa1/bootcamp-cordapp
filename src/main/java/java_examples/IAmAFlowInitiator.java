package java_examples;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.crypto.TransactionSignature;
import net.corda.core.flows.*;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.Vault.Page;
import net.corda.core.node.services.vault.QueryCriteria.VaultQueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;

import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.Collections;
import java.util.List;

import static net.corda.core.crypto.Crypto.generateKeyPair;

// `IAmAFlowInitiator` is our first flow, and will communicate with
// `IAmAFlowResponder`.
// We mark `InitiatorFlow` as an `InitiatingFlow`, allowing it to be
// started directly by the node.
@InitiatingFlow
// We also mark `InitiatorFlow` as `StartableByRPC`, allowing the
// node's owner to start the flow via RPC.
@StartableByRPC
// Every flow must subclass `FlowLogic`. The generic indicates the
// flow's return type.
public class IAmAFlowInitiator extends FlowLogic<Void> {
    private final boolean arg1;
    private final int arg2;
    private final Party counterparty;
    private final Party regulator;

    // Flows can take constructor arguments to parameterise the execution of the flow.
    public IAmAFlowInitiator(boolean arg1, int arg2, Party counterparty, Party regulator) {
        this.arg1 = arg1;
        this.arg2 = arg2;
        this.counterparty = counterparty;
        this.regulator = regulator;
    }

    private final ProgressTracker progressTracker = new ProgressTracker();

    @Suspendable
    @Override
    public Void call() throws FlowException {
        // We'll be using a dummy public key for demonstration purposes.
        PublicKey dummyPubKey = generateKeyPair().getPublic();

        /*---------------------------
         * IDENTIFYING OTHER NODES *
        ---------------------------*/
        // A transaction generally needs a notary:
        //   - To prevent double-spends if the transaction has inputs
        //   - To serve as a timestamping authority if the transaction has a
        //     time-window
        // We retrieve a notary from the network map.
        CordaX500Name notaryName = new CordaX500Name("Notary Service", "London", "GB");
        Party specificNotary = getServiceHub().getNetworkMapCache().getNotary(notaryName);
        // Alternatively, we can pick an arbitrary notary from the notary
        // list. However, it is always preferable to specify the notary
        // explicitly, as the notary list might change when new notaries are
        // introduced, or old ones decommissioned.
        Party firstNotary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

        // We may also need to identify a specific counterparty. We do so
        // using the identity service.
        CordaX500Name counterPartyName = new CordaX500Name("NodeA", "London", "GB");
        Party namedCounterparty = getServiceHub().getIdentityService().wellKnownPartyFromX500Name(counterPartyName);
        Party keyedCounterparty = getServiceHub().getIdentityService().partyFromKey(dummyPubKey);

        /*--------------------------
         * BUILDING A TRANSACTION *
        --------------------------*/
        // We use a `TransactionBuilder` instance to build a transaction.
        TransactionBuilder txBuilder = new TransactionBuilder();

        // After creating the `TransactionBuilder`, we must specify which
        // notary it will use.
        txBuilder.setNotary(specificNotary);

        /*--------------------------------------
         * ADDING INPUT STATES TO THE BUILDER *
        --------------------------------------*/
        // Let's assume there are already some `IAmAState`s in our
        // node's vault, stored there as a result of running past flows,
        // and we want to consume them in a transaction. There are many
        // ways to extract these states from our vault.

        // For example, we would extract any unconsumed `IAmAState`s
        // from our vault as follows:
        VaultQueryCriteria criteria = new VaultQueryCriteria(Vault.StateStatus.UNCONSUMED);
        Page<IAmAState> results = getServiceHub().getVaultService().queryBy(IAmAState.class, criteria);
        List<StateAndRef<IAmAState>> dummyStates = results.getStates();

        // For a full list of the available ways of extracting states from
        // the vault, see the Vault Query docs page.

        // Input states are added to the `TransactionBuilder` as `StateAndRef`s:
        StateAndRef<IAmAState> ourStateAndRef = dummyStates.get(0);
        txBuilder.addInputState(ourStateAndRef);

        /*---------------------------------------
         * ADDING OUTPUT STATES TO THE BUILDER *
        ---------------------------------------*/
        // Output states can be added as a `ContractState` and a contract class name.
        // Output states are constructed from scratch.
        IAmAState ourOutputState = new IAmAState("data", 3, getOurIdentity(), counterparty);
        // Or as copies of other states with some properties changed.
        IAmAState ourOtherOutputState = new IAmAState("new data", ourOutputState.getSecondAttribute(), ourOutputState.getFirstParty(), ourOutputState.getSecondParty());
        txBuilder.addOutputState(ourOutputState, IAmAContract.CONTRACT_ID);

        /*----------------------------------
         * ADDING COMMANDS TO THE BUILDER *
        ----------------------------------*/
        // Commands can be added as a `CommandData` instance and a list of `PublicKey`s.
        IAmAContract.Commands.ACommand commandData = new IAmAContract.Commands.ACommand();
        PublicKey ourPubKey = getServiceHub().getMyInfo().getLegalIdentitiesAndCerts().get(0).getOwningKey();
        PublicKey counterpartyPubKey = counterparty.getOwningKey();
        List<PublicKey> requiredSigners = ImmutableList.of(ourPubKey, counterpartyPubKey);
        txBuilder.addCommand(commandData, requiredSigners);

        /*-----------------------------
         * VERIFYING THE TRANSACTION *
        -----------------------------*/
        // We check that the transaction builder we've created meets the
        // contracts of the input and output states.
        txBuilder.verify(getServiceHub());

        /*---------------------------
         * SIGNING THE TRANSACTION *
        ---------------------------*/
        // We finalise the transaction builder by signing it,
        // converting it into a `SignedTransaction`.
        SignedTransaction onceSignedTx = getServiceHub().signInitialTransaction(txBuilder);

        // If instead this was a `SignedTransaction` that we'd received
        // from a counterparty and we needed to sign it, we would add our
        // signature using:
        SignedTransaction twiceSignedTx = getServiceHub().addSignature(onceSignedTx);

        // We can also generate a signature over the transaction without
        // adding it to the transaction itself. We may do this when
        // sending just the signature in a flow instead of returning the
        // entire transaction with our signature. This way, the receiving
        // node does not need to check we haven't changed anything in the
        // transaction.
        TransactionSignature sig = getServiceHub().createSignature(onceSignedTx);

        /*----------------------------------------
         * GATHERING A TRANSACTION'S SIGNATURES *
        ----------------------------------------*/
        // The list of parties who need to sign a transaction is dictated
        // by the transaction's commands. Once we've signed a transaction
        // ourselves, we can automatically gather the signatures of the
        // other required signers using `CollectSignaturesFlow`.
        // The responder flow will need to call `SignTransactionFlow`.
        SignedTransaction fullySignedTx = subFlow(new CollectSignaturesFlow(twiceSignedTx, Collections.emptySet()));

        /*------------------------------------------
         * VERIFYING THE TRANSACTION'S SIGNATURES *
        ------------------------------------------*/
        try {
            // We can verify that a transaction has all the required
            // signatures, and that they're all valid, by running:
            fullySignedTx.verifyRequiredSignatures();

            // If the transaction is only partially signed, we have to pass in
            // a list of the public keys corresponding to the missing
            // signatures, explicitly telling the system not to check them.
            onceSignedTx.verifySignaturesExcept(counterpartyPubKey);

            // We can also choose to only check the signatures that are
            // present. BE VERY CAREFUL - this function provides no guarantees
            // that the signatures are correct, or that none are missing.
            twiceSignedTx.checkSignaturesAreValid();
        } catch (GeneralSecurityException e) {
            // Handle this as required.
        }

        /*------------------------------
         * FINALISING THE TRANSACTION *
        ------------------------------*/
        // By invoking `FinalityFlow`, we notarise the transaction and get it
        // recorded in the vault of the all the `participants` of all the
        // transaction's states.
        SignedTransaction notarisedTx1 = subFlow(new FinalityFlow(fullySignedTx));

        return null;
    }
}