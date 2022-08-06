package com.frank.fish;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import java.util.Random;

public class FishLayout extends RelativeLayout {

    private Paint mPaint;
    private ImageView ivFish;
    private FishDrawable fishDrawable;
    private float touchX, touchY;
    private float ripple;
    private int alpha;

    public FishLayout(Context context) {
        this(context, null);
    }

    public FishLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FishLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, defStyleAttr);
    }

    public FishLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    private void init(Context context) {
        // ViewGroup 默认不会调用 onDraw()，需要手动设置一下
        setWillNotDraw(false);

        // 波纹画笔设置
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(8);

        // 把 FishDrawable 添加到当前 ViewGroup 中
        ivFish = new ImageView(context);
        LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ivFish.setLayoutParams(params);
        fishDrawable = new FishDrawable();
        ivFish.setImageDrawable(fishDrawable);
        addView(ivFish);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        touchX = event.getX();
        touchY = event.getY();
        makeRippleAnimation();
        makeMovingPath();
        return super.onTouchEvent(event);
    }

    private void makeRippleAnimation() {
        // 波纹画笔初始透明度，随着动画变浅
        mPaint.setAlpha(100);
        // 可以没有 ripple 这个属性，但是一定要有 getRipple() 和 setRipple() 方法，反射时要用到
        ObjectAnimator rippleAnimator = ObjectAnimator.ofFloat(this, "ripple", 0, 1f)
                .setDuration(1000);
        rippleAnimator.start();
    }

    /**
     * 绘制鱼游动的三阶贝塞尔曲线
     */
    private void makeMovingPath() {
        /**
         * 1、先求出图中重心点、控制点1和结束点（即点击点）在当前ViewGroup中的绝对坐标备用
         */
        // 鱼的重心在 FishDrawable 中的坐标
        PointF fishRelativeMiddlePoint = fishDrawable.getMiddlePoint();
        // 鱼的重心在当前 ViewGroup 中的绝对坐标——起始点O
        PointF fishMiddlePoint = new PointF(ivFish.getX() + fishRelativeMiddlePoint.x,
                ivFish.getY() + fishRelativeMiddlePoint.y);
        // 鱼头圆心的相对坐标和绝对坐标——控制点1 A
        PointF fishRelativeHeadPoint = fishDrawable.getHeadPoint();
        PointF fishHeadPoint = new PointF(ivFish.getX() + fishRelativeHeadPoint.x,
                ivFish.getY() + fishRelativeHeadPoint.y);
        // 点击坐标——结束点B
        PointF endPoint = new PointF(touchX, touchY);

        /**
         * 2、求控制点2——C的坐标。先求OC与x轴的夹角，已知∠AOC是∠AOB的一半，那么所求夹角就是∠AOC-∠AOX，
         * 因为在 calculateAngle() 中已经对角度正负做了处理，因此带入时用 angleAOC + angleAOX。
         * todo
         */
        float angleAOC = calculateAngle(fishMiddlePoint, fishHeadPoint, endPoint) / 2;
        float angleAOX = calculateAngle(fishHeadPoint, fishHeadPoint, new PointF(fishMiddlePoint.x + 1, fishMiddlePoint.y));
        PointF controlPointC = fishDrawable.calculatePoint(fishMiddlePoint,
                FishDrawable.HEAD_RADIUS * 1.6f, angleAOC + angleAOX);

        /**
         * 3、绘制曲线，注意属性动画只是将 ivFish 这个 ImageView 的 x，y 平移了，并没有实现鱼头
         * 角度的转动，并且平移时为了保证是鱼的重心平移到被点击的点，path 中的坐标都要减去鱼的重心
         * 相对 ImageView 的坐标（否则平移的点以 ImageView 的左上角为准）。
         */
        Path path = new Path();
        path.moveTo(fishMiddlePoint.x - fishRelativeMiddlePoint.x, fishMiddlePoint.y - fishRelativeMiddlePoint.y);
        path.cubicTo(fishHeadPoint.x - fishRelativeMiddlePoint.x, fishHeadPoint.y - fishRelativeMiddlePoint.y,
                controlPointC.x - fishRelativeMiddlePoint.x, controlPointC.y - fishRelativeMiddlePoint.y,
                endPoint.x - fishRelativeMiddlePoint.x, endPoint.y - fishRelativeMiddlePoint.y);
        ObjectAnimator objectAnimator = ObjectAnimator.ofFloat(ivFish, "x", "y", path);
        objectAnimator.setDuration(2000);
        objectAnimator.addListener(new AnimatorListenerAdapter() {
            // 鱼开始游动时，摆尾频率更快一些。
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                fishDrawable.setFrequency(1f);
            }

            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                fishDrawable.setFrequency(3f);

                // 鱼鳍摆动动画，动画时间和重复次数具有随机性
                ObjectAnimator finsAnimator = ObjectAnimator.ofFloat(fishDrawable, "finsValue",
                        0, FishDrawable.HEAD_RADIUS * 2, 0);
                finsAnimator.setDuration((new Random().nextInt(1) + 1) * 500);
                finsAnimator.setRepeatCount(new Random().nextInt(4));
                finsAnimator.start();
            }
        });

        /**
         * 4、鱼头方向与贝塞尔曲线的切线方向保持一致，从而实现鱼的调头
         */
        final float[] tan = new float[2];
        final PathMeasure pathMeasure = new PathMeasure(path, false);
        objectAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                // 获取到动画当前执行的百分比
                float fraction = animation.getAnimatedFraction();
                // 把动画执行的百分比转换成已经走过的路径，再借助 PathMeasure 计算
                // 出当前所处的点的位置（用不到传了null）和正切tan值
                pathMeasure.getPosTan(pathMeasure.getLength() * fraction, null, tan);
                // 利用正切值计算出的角度正是曲线上当前点的切线角度，注意
                // 表示纵坐标的tan[1]取了反还是因为数学与屏幕坐标系Y轴相反的缘故。
                float angle = (float) Math.toDegrees(Math.atan2(-tan[1], tan[0]));
                // 让鱼头方向转向切线方向
                fishDrawable.setFishMainAngle(angle);
            }
        });

        objectAnimator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        mPaint.setAlpha(alpha);
        canvas.drawCircle(touchX, touchY, ripple * 100, mPaint);
    }

    public float getRipple() {
        return ripple;
    }

    public void setRipple(float ripple) {
        this.ripple = ripple;
        alpha = (int) (100 * (1 - ripple));
        invalidate();
    }

    /**
     * 通过这个公式 cosAOB = (OA*OB)/(|OA|*|OB|) 计算出∠AOB的余弦值，
     * 再通过反三角函数求得∠AOB的大小。
     */
    public float calculateAngle(PointF O, PointF A, PointF B) {
        float vectorProduct = (A.x - O.x) * (B.x - O.x) + (A.y - O.y) * (B.y - O.y);
        float lengthOA = (float) Math.sqrt((A.x - O.x) * (A.x - O.x) + (A.y - O.y) * (A.y - O.y));
        float lengthOB = (float) Math.sqrt((B.x - O.x) * (B.x - O.x) + (B.y - O.y) * (B.y - O.y));
        float cosAOB = vectorProduct / (lengthOA * lengthOB);
        float angleAOB = (float) Math.toDegrees(Math.acos(cosAOB));

        // 使用向量叉乘计算方向，先求出向量OA(Xo-Xa,Yo-Ya)、OB(Xo-Xb,Yo-Yb)，
        // OA x OB = (Xo-Xa)*(Yo-Yb) - (Yo-Ya)*(Xo-Xb)，若结果小于0，则OA在OB的逆时针方向
        float direction = (O.x - A.x) * (O.y - B.y) - (O.y - A.y) * (O.x - B.x);
        // 另一种计算方式，通过AB和OB与x轴夹角大小判断
        // float direction = (A.y - B.y) / (A.x - B.x) - (O.y - B.y) / (O.x - B.x);

        if (direction == 0) {
            // A、O、B 在同一条直线上的情况，可能同向，也可能反向，
            // 要看向量积的正负进一步决定决定鱼的掉头方向。
            if (vectorProduct >= 0) {
                return 0;
            } else {
                return 180;
            }
        } else {
            if (direction > 0) {
                // B在A的顺时针方向，为负
                return -angleAOB;
            } else {
                return angleAOB;
            }
        }
    }
}
