package iq.turuq.core

import android.content.Context

/** يطبّقه كائن Application في :app و :automotive لتصل شاشة السيارة إلى الحاوية المشتركة. */
interface TuruqAppProvider { val turuq: TuruqApp }

fun Context.turuq(): TuruqApp = (applicationContext as TuruqAppProvider).turuq
