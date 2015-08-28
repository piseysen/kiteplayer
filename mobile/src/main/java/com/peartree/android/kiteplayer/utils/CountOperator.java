package com.peartree.android.kiteplayer.utils;

import android.util.Pair;

import rx.Observable;
import rx.Subscriber;

public class CountOperator<T> implements Observable.Operator<Pair<Integer, T>, T> {

    @Override
    public Subscriber<? super T> call(Subscriber<? super Pair<Integer, T>> s) {
        return new Subscriber<T>(s) {

            private int index = 0;

            @Override
            public void onCompleted() {
                if (!s.isUnsubscribed()) {
                    s.onCompleted();
                }
            }

            @Override
            public void onError(Throwable e) {
                if (!s.isUnsubscribed()) {
                    s.onError(e);
                }
            }

            @Override
            public void onNext(T value) {
                if (!s.isUnsubscribed()) {
                    s.onNext(new Pair<>(index++, value));
                }
            }
        };
    }
}
