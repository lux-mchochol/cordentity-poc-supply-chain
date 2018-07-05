package com.luxoft.poc.supplychain.flow

import co.paralleluniverse.fibers.Suspendable
import com.luxoft.poc.supplychain.contract.PackageContract
import com.luxoft.poc.supplychain.contract.ShipmentContract
import com.luxoft.poc.supplychain.data.PackageState
import com.luxoft.poc.supplychain.data.state.Shipment
import com.luxoft.poc.supplychain.data.state.getInfo
import com.luxoft.poc.supplychain.data.state.getObservers
import com.luxoft.poc.supplychain.except
import com.luxoft.poc.supplychain.mapToKeys
import net.corda.confidential.IdentitySyncFlow
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

object DeliverShipment {

    @InitiatingFlow
    @StartableByRPC
    class Sender(private val serial: String, private val receiver: CordaX500Name) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {
            val oldOwner = ourIdentity
            val newOwner = whoIs(receiver)

            val packageIn = getPackageState(serial, ourIdentity)

            val shipment = Shipment(serial, oldOwner, newOwner)
            val shipmentOut = StateAndContract(shipment, ShipmentContract::class.java.name)
            val shipmentCmd = Command(ShipmentContract.RequestForTransfer(), shipment.participants.mapToKeys())

            val info = packageIn.getInfo().copy(
                    state = PackageState.PROCESSED,
                    processedAt = System.currentTimeMillis(),
                    processedBy = oldOwner.name)
            val transferringPackage = packageIn.state.data.requestForTransfer(newOwner, info)

            val packageOut = StateAndContract(transferringPackage, PackageContract::class.java.name)
            val packageCmd = Command(PackageContract.StartShipment(), transferringPackage.participants.mapToKeys())

            val trxBuilder = TransactionBuilder(whoIsNotary()).withItems(
                    packageIn,
                    packageOut,
                    packageCmd,
                    shipmentOut,
                    shipmentCmd
            )

            val flowSession = initiateFlow(newOwner)
            val selfSignedTx = serviceHub.signInitialTransaction(trxBuilder, oldOwner.owningKey)

            // Sync up confidential identities in the transaction with our counterparty
            subFlow(IdentitySyncFlow.Send(flowSession, trxBuilder.toWireTransaction(serviceHub)))

            val signedTrx = subFlow(CollectSignaturesFlow(selfSignedTx, listOf(flowSession)))
            val finalTrx = subFlow(FinalityFlow(signedTrx))

            subFlow(BroadcastToObservers(packageIn.getObservers().except(ourIdentity), finalTrx))
        }
    }

    @InitiatedBy(Sender::class)
    class Receiver(private val flowSession: FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        override fun call() {

            subFlow(IdentitySyncFlow.Receive(flowSession))

            val signTransactionFlow = object : SignTransactionFlow(flowSession) {
                override fun checkTransaction(stx: SignedTransaction) = Unit
            }
            subFlow(signTransactionFlow)
        }

    }
}