/*
 * Copyright the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.ui;

import android.content.Context;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toolbar;
import androidx.annotation.ColorInt;
import androidx.annotation.Dimension;
import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.addressbook.AddressBookEntry;
import de.schildbach.wallet.util.Toolbars;
import de.schildbach.wallet.util.WalletUtils;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.wallet.Wallet;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author Andreas Schildbach
 */
public class AddressBookAdapter extends ListAdapter<AddressBookAdapter.ListItem, RecyclerView.ViewHolder> {
    public static List<ListItem> buildListItems(final Context context,
                                                final Collection<Address> derivedAddresses,
                                                final Collection<Address> randomAddresses,
                                                @Nullable final Wallet wallet,
                                                @Nullable final Map<String, AddressBookEntry> addressBook) {
        final List<ListItem> items = new ArrayList<>(derivedAddresses.size() + randomAddresses.size());
        addListItems(items, derivedAddresses, context, wallet, addressBook);
        if (!derivedAddresses.isEmpty() && !randomAddresses.isEmpty())
            items.add(new ListItem.SeparatorItem(context.getString(R.string.address_book_list_receiving_random)));
        addListItems(items, randomAddresses, context, wallet, addressBook);
        return items;
    }

    private static void addListItems(final List<ListItem> items,
                                     final Collection<Address> addresses, final Context context,
                                     @Nullable final Wallet wallet,
                                     @Nullable final Map<String, AddressBookEntry> addressBook) {
        final String messageCompromised = context.getString(R.string.address_book_row_message_compromised_key);
        final int colorSignificant = ContextCompat.getColor(context, R.color.fg_significant);
        final int colorInsignificant = ContextCompat.getColor(context, R.color.fg_insignificant);
        final int colorLessSignificant = ContextCompat.getColor(context, R.color.fg_less_significant);
        final int colorError = ContextCompat.getColor(context, R.color.fg_error);

        for (final Address address : addresses) {
            final boolean isRotateKey;
            if (wallet != null) {
                final ECKey key = wallet.findKeyFromAddress(address);
                isRotateKey = wallet != null && wallet.isKeyRotating(key);
            } else {
                isRotateKey = false;
            }
            final int addressColor = isRotateKey ? colorInsignificant : colorSignificant;
            final String label;
            final int labelColor;
            if (addressBook != null) {
                final AddressBookEntry entry = addressBook.get(address.toString());
                if (entry != null) {
                    label = entry.getLabel();
                    labelColor = isRotateKey ? colorInsignificant : colorLessSignificant;
                } else {
                    label = null;
                    labelColor = colorInsignificant;
                }
            } else {
                label = null;
                labelColor = colorInsignificant;
            }
            final String message = isRotateKey ? messageCompromised : null;
            final int messageColor = isRotateKey ? colorError : 0;
            items.add(new ListItem.AddressItem(address, addressColor, label, labelColor, message, messageColor));
        }
    }

    public static List<ListItem> buildListItems(final Context context, final List<AddressBookEntry> addressBook) {
        final int colorSignificant = ContextCompat.getColor(context, R.color.fg_significant);
        final int colorLessSignificant = ContextCompat.getColor(context, R.color.fg_less_significant);

        final List<ListItem> items = new ArrayList<>(addressBook.size());
        for (final AddressBookEntry entry : addressBook) {
            final Address address = Address.fromString(Constants.NETWORK_PARAMETERS, entry.getAddress());
            items.add(new ListItem.AddressItem(address, colorSignificant, entry.getLabel(), colorLessSignificant,
                    null, 0));
        }
        return items;
    }

    public static abstract class ListItem {
        public static class AddressItem extends ListItem {
            public final Address address;
            @ColorInt
            public final int addressColor;
            @Nullable
            public final String label;
            @ColorInt
            public final int labelColor;
            @Nullable
            public final String message;
            @ColorInt
            public final int messageColor;

            public AddressItem(final Address address, @ColorInt final int addressColor, final String label,
                               @ColorInt final int labelColor, final String message, @ColorInt final int messageColor) {
                this.address = address;
                this.addressColor = addressColor;
                this.label = label;
                this.labelColor = labelColor;
                this.message = message;
                this.messageColor = messageColor;
            }
        }

        public static class SeparatorItem extends ListItem {
            public final String label;

            public SeparatorItem(final String label) {
                this.label = label;
            }
        }
    }

    private final LayoutInflater inflater;
    private final MenuInflater menuInflater;
    private final String labelUnlabeled;
    @Dimension
    private final int cardElevationSelected;
    @ColorInt
    private final int colorInsignificant;

    @Nullable
    private final OnClickListener onClickListener;
    @Nullable
    private final ContextMenuCallback contextMenuCallback;
    @Nullable
    private Address selectedAddress;

    private static final int VIEW_TYPE_ADDRESS = 0;
    private static final int VIEW_TYPE_SEPARATOR = 1;

    public AddressBookAdapter(final Context context, @Nullable final OnClickListener onClickListener,
                              @Nullable final ContextMenuCallback contextMenuCallback) {
        super(new DiffUtil.ItemCallback<ListItem>() {
            @Override
            public boolean areItemsTheSame(final ListItem oldItem, final ListItem newItem) {
                if (oldItem instanceof ListItem.AddressItem) {
                    if (!(newItem instanceof ListItem.AddressItem))
                        return false;
                    return ((ListItem.AddressItem) oldItem).address.equals(((ListItem.AddressItem) newItem).address);
                } else {
                    if (!(newItem instanceof ListItem.SeparatorItem))
                        return false;
                    return ((ListItem.SeparatorItem) oldItem).label.equals(((ListItem.SeparatorItem) newItem).label);
                }
            }

            @Override
            public boolean areContentsTheSame(final ListItem oldItem, final ListItem newItem) {
                if (oldItem instanceof ListItem.AddressItem) {
                    final ListItem.AddressItem oldAddressItem = (ListItem.AddressItem) oldItem;
                    final ListItem.AddressItem newAddressItem = (ListItem.AddressItem) newItem;
                    if (!Objects.equals(oldAddressItem.address, newAddressItem.address))
                        return false;
                    if (!Objects.equals(oldAddressItem.addressColor, newAddressItem.addressColor))
                        return false;
                    if (!Objects.equals(oldAddressItem.label, newAddressItem.label))
                        return false;
                    if (!Objects.equals(oldAddressItem.labelColor, newAddressItem.labelColor))
                        return false;
                    if (!Objects.equals(oldAddressItem.message, newAddressItem.message))
                        return false;
                    if (!Objects.equals(oldAddressItem.messageColor, newAddressItem.messageColor))
                        return false;
                    return true;
                } else {
                    return true;
                }
            }
        });

        this.inflater = LayoutInflater.from(context);
        this.menuInflater = new MenuInflater(context);
        this.contextMenuCallback = contextMenuCallback;
        this.labelUnlabeled = context.getString(R.string.address_unlabeled);
        this.cardElevationSelected = context.getResources().getDimensionPixelOffset(R.dimen.card_elevation_selected);
        this.colorInsignificant = ContextCompat.getColor(context, R.color.fg_insignificant);
        this.onClickListener = onClickListener;

        setHasStableIds(true);
    }

    @MainThread
    public void setSelectedAddress(final Address newSelectedAddress) {
        if (Objects.equals(newSelectedAddress, selectedAddress))
            return;
        if (selectedAddress != null)
            notifyItemChanged(positionOf(selectedAddress));
        if (newSelectedAddress != null)
            notifyItemChanged(positionOf(newSelectedAddress));
        this.selectedAddress = newSelectedAddress;
    }

    @MainThread
    public int positionOf(final Address address) {
        if (address != null) {
            final List<ListItem> list = getCurrentList();
            for (int i = 0; i < list.size(); i++) {
                final ListItem item = list.get(i);
                if (item instanceof ListItem.AddressItem && ((ListItem.AddressItem) item).address.equals(address))
                    return i;
            }
        }
        return RecyclerView.NO_POSITION;
    }

    @Override
    public int getItemViewType(final int position) {
        final ListItem listItem = getItem(position);
        if (listItem instanceof ListItem.AddressItem)
            return VIEW_TYPE_ADDRESS;
        else if (listItem instanceof ListItem.SeparatorItem)
            return VIEW_TYPE_SEPARATOR;
        else
            throw new IllegalStateException();
    }

    @Override
    public long getItemId(final int position) {
        final ListItem listItem = getItem(position);
        if (listItem instanceof ListItem.AddressItem)
            return ByteBuffer.wrap(((ListItem.AddressItem) listItem).address.getHash()).getLong();
        else if (listItem instanceof ListItem.SeparatorItem)
            return 0;
        else
            throw new IllegalStateException();
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
        if (viewType == VIEW_TYPE_ADDRESS)
            return new AddressViewHolder(inflater.inflate(R.layout.address_book_row, parent, false));
        else if (viewType == VIEW_TYPE_SEPARATOR)
            return new SeparatorViewHolder(inflater.inflate(R.layout.row_separator, parent, false));
        else
            throw new IllegalStateException("unknown type: " + viewType);
    }

    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder holder, final int position) {
        final ListItem listItem = getItem(position);
        if (holder instanceof AddressViewHolder) {
            final AddressViewHolder addressHolder = (AddressViewHolder) holder;
            final ListItem.AddressItem addressItem = (ListItem.AddressItem) listItem;
            addressHolder.label.setText(addressItem.label != null ? addressItem.label : labelUnlabeled);
            addressHolder.label.setTextColor(addressItem.labelColor);
            addressHolder.address.setText(WalletUtils.formatAddress(addressItem.address,
                    Constants.ADDRESS_FORMAT_GROUP_SIZE,
                    Constants.ADDRESS_FORMAT_LINE_SIZE));
            addressHolder.address.setTextColor(addressItem.addressColor);
            addressHolder.message.setVisibility(addressItem.message != null ? View.VISIBLE : View.GONE);
            addressHolder.message.setText(addressItem.message);
            addressHolder.message.setTextColor(addressItem.messageColor);
            final boolean isSelected = addressItem.address.equals(selectedAddress);
            addressHolder.itemView.setSelected(isSelected);
            ((CardView) addressHolder.itemView).setCardElevation(isSelected ? cardElevationSelected : 0);
            if (onClickListener != null)
                addressHolder.itemView.setOnClickListener(v -> onClickListener.onAddressClick(v, addressItem.address,
                        addressItem.label));
            if (contextMenuCallback != null && isSelected) {
                contextMenuCallback.onInflateContextMenu(menuInflater, addressHolder.contextBar.getMenu());
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
                    Toolbars.colorize(addressHolder.contextBar, colorInsignificant);
                addressHolder.contextBar.setVisibility(View.VISIBLE);
                addressHolder.contextBar.setOnMenuItemClickListener(item ->
                        contextMenuCallback.onContextMenuItemClicked(item, addressItem.address, addressItem.label));
            } else {
                addressHolder.contextBar.setVisibility(View.GONE);
            }
        } else if (holder instanceof SeparatorViewHolder) {
            final SeparatorViewHolder separatorHolder = (SeparatorViewHolder) holder;
            final ListItem.SeparatorItem separatorItem = (ListItem.SeparatorItem) listItem;
            separatorHolder.label.setText(separatorItem.label);
        }
    }

    public interface OnClickListener {
        void onAddressClick(View view, Address address, @Nullable String label);
    }

    public interface ContextMenuCallback {
        void onInflateContextMenu(MenuInflater inflater, Menu menu);

        boolean onContextMenuItemClicked(MenuItem item, Address address, @Nullable String label);
    }

    public static class AddressViewHolder extends RecyclerView.ViewHolder {
        private final TextView label;
        private final TextView address;
        private final TextView message;
        private final Toolbar contextBar;

        private AddressViewHolder(final View itemView) {
            super(itemView);
            label = itemView.findViewById(R.id.address_book_row_label);
            address = itemView.findViewById(R.id.address_book_row_address);
            message = itemView.findViewById(R.id.address_book_row_message);
            contextBar = itemView.findViewById(R.id.address_book_row_context_bar);
        }
    }

    public static class SeparatorViewHolder extends RecyclerView.ViewHolder {
        private final TextView label;

        private SeparatorViewHolder(final View itemView) {
            super(itemView);
            label = itemView.findViewById(android.R.id.text1);
        }
    }
}