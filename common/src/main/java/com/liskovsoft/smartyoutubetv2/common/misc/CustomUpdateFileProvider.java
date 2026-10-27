package com.liskovsoft.smartyoutubetv2.common.misc;

import androidx.core.content.FileProvider;

/** Gives the system installer access to the downloaded update apk (own class to avoid manifest merge conflicts). */
public class CustomUpdateFileProvider extends FileProvider {
}
