/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.trade;

import bisq.core.btc.wallet.BtcWalletService;

import bisq.common.crypto.KeyRing;
import bisq.common.util.Tuple2;
import bisq.common.util.Utilities;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

/**
 * This class contains trade utility methods.
 */
@Slf4j
@Singleton
public class TradeUtil {

    private final BtcWalletService btcWalletService;
    private final KeyRing keyRing;

    @Inject
    public TradeUtil(BtcWalletService btcWalletService, KeyRing keyRing) {
        this.btcWalletService = btcWalletService;
        this.keyRing = keyRing;
    }

    /**
     * Returns <MULTI_SIG, TRADE_PAYOUT> if and only if both are AVAILABLE,
     * otherwise null.
     * @param trade the trade being queried for MULTI_SIG, TRADE_PAYOUT addresses
     * @return Tuple2 tuple containing MULTI_SIG, TRADE_PAYOUT addresses for trade
     */
    public Tuple2<String, String> getAvailableAddresses(Trade trade) {
        var addresses = getTradeAddresses(trade);
        if (addresses == null)
            return null;

        if (btcWalletService.getAvailableAddressEntries().stream()
                .noneMatch(e -> Objects.equals(e.getAddressString(), addresses.first)))
            return null;

        if (btcWalletService.getAvailableAddressEntries().stream()
                .noneMatch(e -> Objects.equals(e.getAddressString(), addresses.second)))
            return null;

        return new Tuple2<>(addresses.first, addresses.second);
    }

    /**
     * Returns <MULTI_SIG, TRADE_PAYOUT> addresses as strings if they're known by the
     * wallet.
     * @param trade the trade being queried for MULTI_SIG, TRADE_PAYOUT addresses
     * @return Tuple2 tuple containing MULTI_SIG, TRADE_PAYOUT addresses for trade
     */
    public Tuple2<String, String> getTradeAddresses(Trade trade) {
        var contract = trade.getContract();
        if (contract == null)
            return null;

        // Get multisig address
        var isMyRoleBuyer = contract.isMyRoleBuyer(keyRing.getPubKeyRing());
        var multiSigPubKey = isMyRoleBuyer
                ? contract.getBuyerMultiSigPubKey()
                : contract.getSellerMultiSigPubKey();
        if (multiSigPubKey == null)
            return null;

        var multiSigPubKeyString = Utilities.bytesAsHexString(multiSigPubKey);
        var multiSigAddress = btcWalletService.getAddressEntryListAsImmutableList().stream()
                .filter(e -> e.getKeyPair().getPublicKeyAsHex().equals(multiSigPubKeyString))
                .findAny()
                .orElse(null);
        if (multiSigAddress == null)
            return null;

        // Get payout address
        var payoutAddress = isMyRoleBuyer
                ? contract.getBuyerPayoutAddressString()
                : contract.getSellerPayoutAddressString();
        var payoutAddressEntry = btcWalletService.getAddressEntryListAsImmutableList().stream()
                .filter(e -> Objects.equals(e.getAddressString(), payoutAddress))
                .findAny()
                .orElse(null);
        if (payoutAddressEntry == null)
            return null;

        return new Tuple2<>(multiSigAddress.getAddressString(), payoutAddress);
    }
}
