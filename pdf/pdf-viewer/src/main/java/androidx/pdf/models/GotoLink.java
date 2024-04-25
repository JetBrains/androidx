/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.pdf.models;

import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

import com.google.common.base.Preconditions;

import java.util.List;
import java.util.Objects;

/**
 * Represents the content associated with a goto link on a page in the PDF document. GotoLink is an
 * internal navigation link which directs the user to a different location within the same pdf
 * document.
 */
// TODO: Use android.graphics.pdf.content.PdfPageGotoLinkContent and remove this class
@RestrictTo(RestrictTo.Scope.LIBRARY)
@SuppressWarnings("deprecation")
@SuppressLint("BanParcelableUsage")
public class GotoLink implements Parcelable {

    public static final Creator<GotoLink> CREATOR = new Creator<GotoLink>() {
        @SuppressWarnings("unchecked")
        @Override
        public GotoLink createFromParcel(Parcel parcel) {
            return new GotoLink((List<Rect>) Objects.requireNonNull(
                    parcel.readArrayList(Rect.class.getClassLoader())),
                    (GotoLinkDestination) Objects.requireNonNull(parcel.readParcelable(
                            GotoLinkDestination.class.getClassLoader())));
        }

        @Override
        public GotoLink[] newArray(int size) {
            return new GotoLink[size];
        }
    };

    private final List<Rect> mBounds;
    private final GotoLinkDestination mDestination;

    /**
     * Creates a new instance of {@link GotoLink} using the bounds of the goto link
     * and the destination where it is directing
     *
     * @param bounds      Bounds which envelop the goto link
     * @param destination Destination where the goto link is directing
     * @throws NullPointerException     If bounds or destination is null.
     * @throws IllegalArgumentException If the bounds list is empty.
     */
    public GotoLink(@NonNull List<Rect> bounds, @NonNull GotoLinkDestination destination) {
        Preconditions.checkNotNull(bounds, "Bounds cannot be null");
        Preconditions.checkArgument(!bounds.isEmpty(), "Bounds cannot be empty");
        Preconditions.checkNotNull(destination, "Destination cannot be null");
        this.mBounds = bounds;
        this.mDestination = destination;
    }

    /**
     * Gets the bounds of a {@link GotoLink} represented as a list of {@link Rect}.
     * Links which are spread across multiple lines will be surrounded by multiple {@link Rect}
     * in order of viewing.
     *
     * @return The bounds of the goto link.
     */
    @NonNull
    public List<Rect> getBounds() {
        return mBounds;
    }

    /**
     * Gets the destination {@link GotoLinkDestination} of the {@link GotoLink}.
     *
     * @return Destination where goto link is directing the user.
     */
    @NonNull
    public GotoLinkDestination getDestination() {
        return mDestination;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return "GotoLink{" + "mBounds=" + mBounds + ", mDestination=" + mDestination + '}';
    }

    @Override
    public void writeToParcel(@NonNull Parcel parcel, int flags) {
        parcel.writeList(mBounds);
        parcel.writeParcelable(mDestination, 0);
    }
}
