package com.guoxiaoxing.phoenix.picker.ui.picker

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.support.v4.view.PagerAdapter
import android.support.v4.view.ViewPager
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.guoxiaoxing.phoenix.R
import com.guoxiaoxing.phoenix.picker.anim.OptAnimationLoader
import com.guoxiaoxing.phoenix.core.common.PhoenixConstant
import com.guoxiaoxing.phoenix.core.model.MediaEntity
import com.guoxiaoxing.phoenix.core.model.MimeType
import com.guoxiaoxing.phoenix.picker.model.EventEntity
import com.guoxiaoxing.phoenix.picker.rx.bus.RxBus
import com.guoxiaoxing.phoenix.picker.rx.bus.Subscribe
import com.guoxiaoxing.phoenix.picker.rx.bus.ThreadMode
import com.guoxiaoxing.phoenix.picker.ui.BaseFragment
import com.guoxiaoxing.phoenix.picker.ui.camera.OnPictureEditListener
import com.guoxiaoxing.phoenix.picker.util.AttrsUtils
import com.guoxiaoxing.phoenix.picker.util.LightStatusBarUtils
import com.guoxiaoxing.phoenix.picker.util.ScreenUtils
import com.guoxiaoxing.phoenix.picker.util.ToolbarUtil
import com.guoxiaoxing.phoenix.picker.util.VoiceUtils
import com.guoxiaoxing.phoenix.picker.widget.photoview.OnPhotoTapListener
import kotlinx.android.synthetic.main.adapter_preview.*
import kotlinx.android.synthetic.main.fragment_preview.*

import java.util.ArrayList

class PreviewFragment : BaseFragment(), View.OnClickListener, Animation.AnimationListener, OnPictureEditListener {

    private var position: Int = 0
    private var images: List<MediaEntity> = ArrayList()
    private var selectImages: MutableList<MediaEntity> = ArrayList()
    private lateinit var adapter: SimpleFragmentAdapter
    private lateinit var animation: Animation
    private var refresh: Boolean = false
    private var index: Int = 0
    private var screenWidth: Int = 0

    //EventBus 3.0 回调
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun eventBus(obj: EventEntity) {
        when (obj.what) {
            PhoenixConstant.CLOSE_PREVIEW_FLAG -> {
                // 压缩完后关闭预览界面
                activity.finish()
                activity.overridePendingTransition(0, R.anim.phoenix_activity_out)
            }
        }
    }

    @SuppressLint("StringFormatMatches")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }


    @SuppressLint("StringFormatMatches")
    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater!!.inflate(R.layout.fragment_preview, container, false)
        if (!RxBus.default.isRegistered(this)) {
            RxBus.default.register(this)
        }
        screenWidth = ScreenUtils.getScreenWidth(activity)
        val status_color = AttrsUtils.getTypeValueColor(activity, R.attr.phoenix_preview_status_color)
        ToolbarUtil.setColorNoTranslucent(activity, status_color)
        LightStatusBarUtils.setLightStatusBar(activity, previewStatusFont)
        animation = OptAnimationLoader.loadAnimation(activity, R.anim.phoenix_window_in)
        animation.setAnimationListener(this)
        picture_left_back.setOnClickListener(this)
        pick_ll_ok.setOnClickListener(this)

        position = arguments.getInt(PhoenixConstant.KEY_POSITION, 0)
        tv_ok.text = if (numComplete)
            getString(R.string.picture_done_front_num, 0, maxSelectNum)
        else
            getString(R.string.picture_please_select)
        tv_picture_num.isSelected = checkNumMode
        preview_ll_edit.setOnClickListener(this)

        selectImages = arguments.getParcelableArrayList<MediaEntity>(PhoenixConstant.KEY_SELECT_LIST)
        images = arguments.getParcelableArrayList<MediaEntity>(PhoenixConstant.KEY_LIST)

        initViewPageAdapterData()
        ll_check.setOnClickListener(View.OnClickListener {
            if (images != null && images.size > 0) {
                val image = images[preview_pager.currentItem]
                val pictureType = if (selectImages.size > 0)
                    selectImages[0].mimeType
                else
                    ""
                if (!TextUtils.isEmpty(pictureType)) {
                    val toEqual = MimeType.mimeToEqual(pictureType, image.mimeType)
                    if (!toEqual) {
                        showToast(getString(R.string.picture_rule))
                        return@OnClickListener
                    }
                }
                // 刷新图片列表中图片状态
                val isChecked: Boolean
                if (!tv_check.isSelected) {
                    isChecked = true
                    tv_check.isSelected = true
                    tv_check.startAnimation(animation)
                } else {
                    isChecked = false
                    tv_check.isSelected = false
                }
                if (selectImages.size >= maxSelectNum && isChecked) {
                    showToast(getString(R.string.phoenix_message_max_number, maxSelectNum))
                    tv_check.isSelected = false
                    return@OnClickListener
                }
                if (isChecked) {
                    VoiceUtils.playVoice(mContext, openClickSound)
                    selectImages.add(image)
                    image.number = selectImages.size
                    if (checkNumMode) {
                        tv_check.text = image.number.toString() + ""
                    }
                } else {
                    for (mediaEntity in selectImages) {
                        if (mediaEntity.localPath == image.localPath) {
                            selectImages.remove(mediaEntity)
                            subSelectPosition()
                            notifyCheckChanged(mediaEntity)
                            break
                        }
                    }
                }
                onSelectNumChange(true)
            }
        })

        preview_pager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                isPreviewEggs(previewEggs, position, positionOffsetPixels)
            }

            override fun onPageSelected(i: Int) {
                position = i
                picture_title.text = (position + 1).toString() + "/" + images.size
                val mediaEntity = images[position]
                index = mediaEntity.getPosition()
                if (!previewEggs) {
                    if (checkNumMode) {
                        tv_check.text = mediaEntity.number.toString() + ""
                        notifyCheckChanged(mediaEntity)
                    }
                    onImageChecked(position)
                }
                if (mediaEntity.fileType == MimeType.ofImage()) {
                    ll_picture_edit.visibility = View.VISIBLE
                } else {
                    ll_picture_edit.visibility = View.GONE
                }
            }

            override fun onPageScrollStateChanged(state: Int) {}
        })
        return view
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (RxBus.default.isRegistered(this)) {
            RxBus.default.unregister(this)
        }
        animation.cancel()
    }

    /**
     * 这里没实际意义，好处是预览图片时 滑动到屏幕一半以上可看到下一张图片是否选中了

     * @param previewEggs          是否显示预览友好体验
     * *
     * @param positionOffsetPixels 滑动偏移量
     */
    private fun isPreviewEggs(previewEggs: Boolean, position: Int, positionOffsetPixels: Int) {
        if (previewEggs) {
            if (images.size > 0 && images != null) {
                val mediaEntity: MediaEntity
                val num: Int
                if (positionOffsetPixels < screenWidth / 2) {
                    mediaEntity = images[position]
                    tv_check.isSelected = isSelected(mediaEntity)
                    if (checkNumMode) {
                        num = mediaEntity.number
                        tv_check.text = num.toString() + ""
                        notifyCheckChanged(mediaEntity)
                        onImageChecked(position)
                    }
                } else {
                    mediaEntity = images[position + 1]
                    tv_check.isSelected = isSelected(mediaEntity)
                    if (checkNumMode) {
                        num = mediaEntity.number
                        tv_check.text = num.toString() + ""
                        notifyCheckChanged(mediaEntity)
                        onImageChecked(position + 1)
                    }
                }
            }
        }
    }

    private fun initViewPageAdapterData() {
        picture_title.text = (position + 1).toString() + "/" + images.size
        adapter = SimpleFragmentAdapter()
        preview_pager.adapter = adapter
        preview_pager.currentItem = position
        onSelectNumChange(false)
        onImageChecked(position)
        if (images.size > 0) {
            val mediaEntity = images[position]
            index = mediaEntity.getPosition()
            if (checkNumMode) {
                tv_picture_num.isSelected = true
                tv_check.text = mediaEntity.number.toString() + ""
                notifyCheckChanged(mediaEntity)
            }
        }
    }

    /**
     * 选择按钮更新
     */
    private fun notifyCheckChanged(imageBean: MediaEntity) {
        if (checkNumMode) {
            tv_check.text = ""
            for (mediaEntity in selectImages) {
                if (mediaEntity.localPath == imageBean.localPath) {
                    imageBean.number = mediaEntity.number
                    tv_check.text = imageBean.number.toString()
                }
            }
        }
    }

    /**
     * 更新选择的顺序
     */
    private fun subSelectPosition() {
        run {
            var index = 0
            val len = selectImages.size
            while (index < len) {
                val mediaEntity = selectImages[index]
                mediaEntity.number = index + 1
                index++
            }
        }
    }

    /**
     * 判断当前图片是否选中

     * @param position
     */
    fun onImageChecked(position: Int) {
        if (images != null && images.size > 0) {
            val mediaEntity = images[position]
            tv_check.isSelected = isSelected(mediaEntity)
        } else {
            tv_check.isSelected = false
        }
    }

    /**
     * 当前图片是否选中

     * @param image
     * *
     * @return
     */
    fun isSelected(image: MediaEntity): Boolean {
        for (mediaEntity in selectImages) {
            if (mediaEntity.localPath == image.localPath) {
                return true
            }
        }
        return false
    }

    /**
     * 更新图片选择数量
     */
    @SuppressLint("StringFormatMatches")
    fun onSelectNumChange(isRefresh: Boolean) {
        this.refresh = isRefresh
        val enable = selectImages.size != 0
        if (enable) {
            pick_ll_ok.isEnabled = true
            if (numComplete) {
                tv_ok.text = getString(R.string.picture_done_front_num, selectImages.size, maxSelectNum)
            } else {
                if (refresh) {
                    tv_picture_num.startAnimation(animation)
                }
                tv_picture_num.visibility = View.VISIBLE
                tv_picture_num.text = selectImages.size.toString() + ""
                tv_ok.text = getString(R.string.picture_completed)
            }
        } else {
            pick_ll_ok.isEnabled = false
            //            tv_ok.setTextColor(ContextCompat.getColor(getActivity(), R.color.tab_color_false));
            if (numComplete) {
                tv_ok.text = getString(R.string.picture_done_front_num, 0, maxSelectNum)
            } else {
                tv_picture_num.visibility = View.INVISIBLE
                tv_ok.text = getString(R.string.picture_please_select)
            }
        }
        updatePickerActivity(refresh)
    }

    /**
     * 更新图片列表选中效果

     * @param isRefresh isRefresh
     */
    private fun updatePickerActivity(isRefresh: Boolean) {
        if (isRefresh) {
            val obj = EventEntity(PhoenixConstant.FLAG_PREVIEW_UPDATE_SELECT, selectImages, index)
            RxBus.default.post(obj)
        }
    }

    override fun onAnimationStart(animation: Animation) {}

    override fun onAnimationEnd(animation: Animation) {
        updatePickerActivity(refresh)
    }

    override fun onAnimationRepeat(animation: Animation) {

    }

    override fun onEditSucess(editPath: String) {
        val mediaEntity = images[preview_pager.currentItem]
        mediaEntity.editPath = editPath
        for (pickMediaEntity in selectImages) {
            if (TextUtils.equals(mediaEntity.localPath, pickMediaEntity.localPath)) {
                pickMediaEntity.editPath = editPath
            }
        }
        adapter.notifyDataSetChanged()
        updatePickerActivity(true)
    }

    private inner class SimpleFragmentAdapter : PagerAdapter() {

        override fun getCount(): Int {
            return images.size
        }

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            container.removeView(`object` as View)
        }

        override fun isViewFromObject(view: View, `object`: Any): Boolean {
            return view === `object`
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val contentView = LayoutInflater.from(container.context).inflate(R.layout.adapter_preview, container, false)
            val mediaEntity = images[position]
            val mimeType = mediaEntity.mimeType
            val eqVideo: Boolean
            if (TextUtils.isEmpty(mimeType)) {
                eqVideo = mediaEntity.fileType == MimeType.ofVideo()
            } else {
                eqVideo = mimeType.startsWith(PhoenixConstant.VIDEO)
            }

            if (eqVideo) {
                preview_image.visibility = View.VISIBLE
                preview_image.visibility = View.GONE
            } else {
                preview_image.visibility = View.GONE
                preview_image.visibility = View.VISIBLE
            }

            val path = mediaEntity.finalPath
            val isGif = MimeType.isGif(mimeType)
            // 压缩过的gif就不是gif了
            if (isGif && !mediaEntity.isCompressed) {
                val gifOptions = RequestOptions()
                        .override(480, 800)
                        .priority(Priority.HIGH)
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                Glide.with(this@PreviewFragment)
                        .asGif()
                        .load(path)
                        .apply(gifOptions)
                        .into(preview_image)
            } else {
                val options = RequestOptions()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .override(480, 800)
                Glide.with(this@PreviewFragment)
                        .asBitmap()
                        .load(path)
                        .apply(options)
                        .into(preview_image)
            }

            preview_image.setOnPhotoTapListener(object : OnPhotoTapListener {
                override fun onPhotoTap(view: ImageView, x: Float, y: Float) {
                    activity.finish()
                    activity.overridePendingTransition(0, R.anim.phoenix_activity_out)
                }
            })


            preview_video.register(activity)
            preview_video.setVideoPath(path)
            preview_video.seekTo(100)

            container.addView(contentView, 0)
            return contentView
        }
    }


    override fun onClick(view: View) {
        val id = view.id
        if (id == R.id.picture_left_back) {
            activity.finish()
            activity.overridePendingTransition(0, R.anim.phoenix_activity_out)
        } else if (id == R.id.pick_ll_ok) {
            val images = selectImages
            val pictureType = if (images.size > 0) images[0].mimeType else ""
            val size = images.size
            val eqImg = !TextUtils.isEmpty(pictureType) && pictureType.startsWith(PhoenixConstant.IMAGE)

            // 如果设置了图片最小选择数量，则判断是否满足条件
            if (minSelectNum > 0 && selectionMode == PhoenixConstant.MULTIPLE) {
                if (size < minSelectNum) {
                    @SuppressLint("StringFormatMatches") val str = if (eqImg)
                        getString(R.string.picture_min_img_num, minSelectNum)
                    else
                        getString(R.string.phoenix_message_min_number, minSelectNum)
                    showToast(str)
                    return
                }
            }
            onResult(images)
        } else if (id == R.id.preview_ll_edit) {
            //            RotateFragment rotateFragment = RotateFragment.newInstance();
            //            rotateFragment.setOnPictureEditListener(this);
            //            Bundle bundle = new Bundle();
            //            String path = getCurrentPath();
            //            if (path != null) {
            //                bundle.putString(PhoenixConstant.KEY_FILE_PATH, path);
            //                rotateFragment.setArguments(bundle);
            //                getActivity().getSupportFragmentManager().beginTransaction()
            //                        .replace(R.id.preview_fragment_container, rotateFragment).addToBackStack(null).commitAllowingStateLoss();
            //            }
        }
    }

    fun onResult(images: List<MediaEntity>) {
        RxBus.default.post(EventEntity(PhoenixConstant.FLAG_PREVIEW_COMPLETE, images))
        activity.finish()
        activity.overridePendingTransition(0, R.anim.phoenix_activity_out)
    }

    private val currentPath: String
        get() = images[preview_pager.currentItem].finalPath

    companion object {

        fun newInstance(): PreviewFragment {
            return PreviewFragment()
        }
    }
}