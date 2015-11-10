/*
 * Original work Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modified work Copyright (c) 2015 Rafael Pereira
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 *
 *      https://mozilla.org/MPL/2.0/
 *
 */
package com.peartree.android.kiteplayer.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.AnimationDrawable;
import android.media.MediaDescription;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.peartree.android.kiteplayer.R;
import com.peartree.android.kiteplayer.utils.ResourceHelper;

public class MediaItemViewHolder {

    static final int STATE_INVALID = -1;
    static final int STATE_NONE = 0;
    static final int STATE_PLAYABLE = 1;
    static final int STATE_PAUSED = 2;
    static final int STATE_PLAYING = 3;
    static final int STATE_CLOSED_FOLDER = 4;

    private static ColorStateList sColorStatePlaying;
    private static ColorStateList sColorStateNotPlaying;

    ImageView mImageView;
    TextView mTitleView;
    TextView mDescriptionView;

    static View setupView(Activity activity, View convertView, ViewGroup parent,
                                    MediaDescription description, int state) {

        if (sColorStatePlaying == null || sColorStateNotPlaying == null) {
            initializeColorStateLists(activity);
        }

        MediaItemViewHolder holder;
        Integer cachedState = STATE_INVALID;

        if (convertView == null) {
            convertView = LayoutInflater.from(activity)
                    .inflate(R.layout.media_list_item, parent, false);
            holder = new MediaItemViewHolder();
            holder.mImageView = (ImageView) convertView.findViewById(R.id.play_eq);
            holder.mTitleView = (TextView) convertView.findViewById(R.id.title);
            holder.mDescriptionView = (TextView) convertView.findViewById(R.id.description);
            convertView.setTag(holder);
        } else {
            holder = (MediaItemViewHolder) convertView.getTag();
            cachedState = (Integer) convertView.getTag(R.id.tag_mediaitem_state_cache);
        }

        holder.mTitleView.setText(description.getTitle());
        if (description.getSubtitle() != null && description.getSubtitle().length()>0) {
            holder.mDescriptionView.setText(description.getSubtitle());
            holder.mDescriptionView.setVisibility(View.VISIBLE);
        } else {
            holder.mDescriptionView.setVisibility(View.GONE);
        }

        // If the state of convertView is different, we need to adapt the view to the
        // new state.
        if (cachedState == null || cachedState != state) {

            holder.mTitleView.setEnabled(true);
            holder.mDescriptionView.setEnabled(true);

            switch (state) {
                case STATE_PLAYABLE:
                    holder.mImageView.setImageDrawable(
                        activity.getDrawable(R.drawable.ic_play_arrow_black_36dp));
                    holder.mImageView.setImageTintList(sColorStateNotPlaying);
                    holder.mImageView.setVisibility(View.VISIBLE);
                    break;
                case STATE_PLAYING:
                    AnimationDrawable animation = (AnimationDrawable)
                        activity.getDrawable(R.drawable.ic_equalizer_white_36dp);
                    holder.mImageView.setImageDrawable(animation);
                    holder.mImageView.setImageTintList(sColorStatePlaying);
                    holder.mImageView.setVisibility(View.VISIBLE);
                    if (animation != null) animation.start();
                    break;
                case STATE_PAUSED:
                    holder.mImageView.setImageDrawable(
                        activity.getDrawable(R.drawable.ic_equalizer1_white_36dp));
                    holder.mImageView.setImageTintList(sColorStateNotPlaying);
                    holder.mImageView.setVisibility(View.VISIBLE);
                    break;
                case STATE_CLOSED_FOLDER:
                    holder.mImageView.setImageDrawable(
                            activity.getDrawable(R.drawable.ic_folder_grey_36dp));
                    holder.mImageView.setImageTintList(sColorStateNotPlaying);
                    holder.mImageView.setVisibility(View.VISIBLE);
                    break;
                default:
                    holder.mDescriptionView.setEnabled(false);
                    holder.mTitleView.setEnabled(false);
                    holder.mImageView.setVisibility(View.GONE);
            }
            convertView.setTag(R.id.tag_mediaitem_state_cache, state);
        }

        return convertView;
    }

    static private void initializeColorStateLists(Context ctx) {
        sColorStateNotPlaying = ColorStateList.valueOf(ResourceHelper.getThemeColor(ctx, android.R.attr.textColorSecondary, R.color.secondary_text_light));
        sColorStatePlaying = ColorStateList.valueOf(ResourceHelper.getThemeColor(ctx, R.attr.colorAccent, R.color.app_accent));
    }
}
