package com.frank.fish;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class FishDrawable extends Drawable {

    /**
     * 绘制鱼时用到的常量
     */
    // 鱼头半径
    public static final int HEAD_RADIUS = 50;

    // 默认的 Drawable 大小是鱼头半径 x 倍
    private static final float SIZE_MULTIPLE_NUMBER = 8.38f;

    // 鱼身长度
    private static final float BODY_LENGTH = 3.2f * HEAD_RADIUS;

    // 鱼鳍起点与鱼头圆心连线长度
    private static final float FIND_FINS_LENGTH = 0.9f * HEAD_RADIUS;

    // 鱼鳍长度
    private static final float FINS_LENGTH = 1.3f * HEAD_RADIUS;

    // 节肢大圆半径
    private static final float BIG_CIRCLE_RADIUS = 0.7f * HEAD_RADIUS;

    // 节肢中圆半径
    private static final float MIDDLE_CIRCLE_RADIUS = 0.6f * BIG_CIRCLE_RADIUS;

    // 节肢小圆半径
    private static final float SMALL_CIRCLE_RADIUS = 0.4f * MIDDLE_CIRCLE_RADIUS;

    // 大圆与中圆圆心距离
    private static final float BigMiddleCenterLength = BIG_CIRCLE_RADIUS + MIDDLE_CIRCLE_RADIUS;

    // 中圆到大三角形底边中点的距离
    private static final float FIND_TRIANGLE_LENGTH = MIDDLE_CIRCLE_RADIUS * 2.7f;

    // 中圆与小圆圆心距离
    private static final float MiddleSmallCenterLength =  MIDDLE_CIRCLE_RADIUS * (0.4f + 2.7f);

    /**
     * 透明度
     */
    // 身体透明值比其它部分大一些
    private static final int BODY_ALPHA = 160;
    private static final int OTHER_ALPHA = 110;

    // 鱼的朝向与x轴正方向的夹角
    private float fishMainAngle = 90;

    // 鱼的重心点
    private PointF middlePoint;

    // 鱼头圆心
    private PointF headPoint;

    // 画图相关
    private Path mPath;
    private Paint mPaint;

    // 属性动画值
    private float currentAnimatorValue;

    // 鱼尾摆动的频率控制（鱼尾在开始游动时摆的快一点）
    private float frequency = 1f;

    // 鱼鳍摆动控制
    private float finsValue;

    public FishDrawable() {
        mPath = new Path();
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setARGB(OTHER_ALPHA, 244, 92, 71);

        // 鱼的重心点位于整个 Drawable 的中心
        middlePoint = new PointF(SIZE_MULTIPLE_NUMBER / 2 * HEAD_RADIUS, SIZE_MULTIPLE_NUMBER / 2 * HEAD_RADIUS);

        ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 720f);
        valueAnimator.setRepeatCount(ValueAnimator.INFINITE);
        valueAnimator.setRepeatMode(ValueAnimator.RESTART);
        valueAnimator.setInterpolator(new LinearInterpolator());
        valueAnimator.setDuration(2000);
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                currentAnimatorValue = (float) animation.getAnimatedValue();
                invalidateSelf();
            }
        });
        valueAnimator.start();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        // float fishAngle = fishMainAngle + currentAnimatorValue * 10;
        float fishAngle = (float) (fishMainAngle + Math.sin(Math.toRadians(currentAnimatorValue)) * 10);

        // 1.先画鱼头，就是一个圆，圆心与重心距离为鱼身长一半，1.6R
        headPoint = calculatePoint(middlePoint, BODY_LENGTH / 2, fishAngle);
        canvas.drawCircle(headPoint.x, headPoint.y, HEAD_RADIUS, mPaint);

        // 2.画鱼鳍，身体两侧各一个。鱼鳍是一个二阶贝塞尔曲线，其起点与鱼头圆心的距离为0.9R，
        // 两点连线与x轴正方向的角度为110°
        PointF leftFinPoint = calculatePoint(headPoint, FIND_FINS_LENGTH, fishAngle + 110);
        PointF rightFinPoint = calculatePoint(headPoint, FIND_FINS_LENGTH, fishAngle - 110);
        makeFin(canvas, leftFinPoint, fishAngle, true);
        makeFin(canvas, rightFinPoint, fishAngle, false);

        // 3.画节肢，节肢1是两个圆相切，并且还有个以两个圆的直径为上下底的梯形，
        // 节肢2是一个梯形加一个小圆。
        PointF bigCircleCenterPoint = calculatePoint(headPoint, BODY_LENGTH, fishAngle - 180);
        // 计算两个圆中较小圆心的工作要交给 makeSegment，因为节肢摆动的角度与鱼身摆动角度不同，
        // 不能直接用 fishAngle 计算圆心，否则圆心点计算就不准了。
        // PointF middleCircleCenterPoint1 = calculatePoint(bigCircleCenterPoint, BigMiddleCenterLength, fishAngle - 180);
        PointF middleCircleCenterPoint = makeSegment(canvas, bigCircleCenterPoint, BIG_CIRCLE_RADIUS, MIDDLE_CIRCLE_RADIUS,
                BigMiddleCenterLength, fishAngle, true);
        makeSegment(canvas, middleCircleCenterPoint, MIDDLE_CIRCLE_RADIUS, SMALL_CIRCLE_RADIUS,
                MiddleSmallCenterLength, fishAngle, false);

        // 4.画尾巴，是两个等腰三角形，一个顶点在中圆圆心，该顶点到大三角形底边中点距离为中圆半径的2.7倍
        float findEdgeLength = (float) Math.abs(Math.sin(Math.toRadians(currentAnimatorValue * 1.5)) * BIG_CIRCLE_RADIUS);
        makeTriangle(canvas, middleCircleCenterPoint, FIND_TRIANGLE_LENGTH, findEdgeLength, fishAngle);
        makeTriangle(canvas, middleCircleCenterPoint, FIND_TRIANGLE_LENGTH - 10, findEdgeLength - 20, fishAngle);
        Log.d("Frank", "draw: " + findEdgeLength);

        // 5.画身体，身体两侧的线条也是二阶贝塞尔曲线
        makeBody(canvas, headPoint, bigCircleCenterPoint, fishAngle);
    }

    private void makeBody(Canvas canvas, PointF headPoint, PointF bigCircleCenterPoint, float fishAngle) {

        // 先求头部圆和大圆直径上的四个点
        PointF upperLeftPoint = calculatePoint(headPoint, HEAD_RADIUS, fishAngle + 90);
        PointF upperRightPoint = calculatePoint(headPoint, HEAD_RADIUS, fishAngle - 90);
        PointF bottomLeftPoint = calculatePoint(bigCircleCenterPoint, BIG_CIRCLE_RADIUS, fishAngle + 90);
        PointF bottomRightPoint = calculatePoint(bigCircleCenterPoint, BIG_CIRCLE_RADIUS, fishAngle - 90);

        // 两侧的控制点，长度和角度是在画图调整后测量出来的
        PointF controlLeft = calculatePoint(headPoint, BODY_LENGTH * 0.56f,
                fishAngle + 130);
        PointF controlRight = calculatePoint(headPoint, BODY_LENGTH * 0.56f,
                fishAngle - 130);

        // 绘制
        mPath.reset();
        mPath.moveTo(upperLeftPoint.x, upperLeftPoint.y);
        mPath.quadTo(controlLeft.x, controlLeft.y, bottomLeftPoint.x, bottomLeftPoint.y);
        mPath.lineTo(bottomRightPoint.x, bottomRightPoint.y);
        mPath.quadTo(controlRight.x, controlRight.y, upperRightPoint.x, upperRightPoint.y);
        mPaint.setAlpha(BODY_ALPHA);
        canvas.drawPath(mPath, mPaint);
    }

    /**
     * @param startPoint         与中圆圆心重合的那个顶点
     * @param toEdgeMiddleLength startPoint 到对边中点的距离
     * @param edgeLength         startPoint 对边长度
     */
    private void makeTriangle(Canvas canvas, PointF startPoint, float toEdgeMiddleLength, float edgeLength, float fishAngle) {

        //        float triangleAngle = fishAngle + currentAnimatorValue * 10;
        float triangleAngle = (float) (fishAngle + Math.sin(Math.toRadians(currentAnimatorValue * frequency * 1.5)) * 25);

        // 对边中点
        PointF edgeMiddlePoint = calculatePoint(startPoint, toEdgeMiddleLength, triangleAngle - 180);

        // 三角形另外两个顶点
        PointF leftPoint = calculatePoint(edgeMiddlePoint, edgeLength, triangleAngle + 90);
        PointF rightPoint = calculatePoint(edgeMiddlePoint, edgeLength, triangleAngle - 90);

        // 开始绘制
        mPath.reset();
        mPath.moveTo(startPoint.x, startPoint.y);
        mPath.lineTo(leftPoint.x, leftPoint.y);
        mPath.lineTo(rightPoint.x, rightPoint.y);
        canvas.drawPath(mPath, mPaint);
    }

    /**
     * 绘制节肢部分的大圆和小圆，以及两个圆之间的梯形，
     * 同时返回两个圆中较小圆的圆心
     *
     * @param bigCircleCenterPoint 大圆圆心
     * @param bigCircleRadius      大圆半径
     * @param smallCircleRadius    小圆半径
     * @param circleCenterLength   两个圆心之间的距离
     * @param fishAngle            鱼头方向与x轴夹角
     * @param hasBigCircle         是否绘制大圆，节肢1要画大圆和小圆，而节肢2只需要画一个小圆
     */
    private PointF makeSegment(Canvas canvas, PointF bigCircleCenterPoint, float bigCircleRadius, float smallCircleRadius,
                               float circleCenterLength, float fishAngle, boolean hasBigCircle) {
        // float segmentAngle = fishAngle + currentAnimatorValue * 10;
        float segmentAngle;
        if (hasBigCircle) {
            // 节肢1
            segmentAngle = (float) (fishAngle + Math.cos(Math.toRadians(currentAnimatorValue * frequency * 1.5)) * 15);
        } else {
            // 节肢2
            segmentAngle = (float) (fishAngle + Math.sin(Math.toRadians(currentAnimatorValue * frequency * 1.5)) * 25);
        }

        // 先计算两个圆中较小圆的圆心
        PointF smallCircleCenterPoint = calculatePoint(bigCircleCenterPoint, circleCenterLength, segmentAngle - 180);

        // 再计算梯形四个角的点，给点命名时，靠近鱼头方向的直径称为 upper，在鱼身左侧的称为 left
        PointF upperLeftPoint = calculatePoint(bigCircleCenterPoint, bigCircleRadius, segmentAngle + 90);
        PointF upperRightPoint = calculatePoint(bigCircleCenterPoint, bigCircleRadius, segmentAngle - 90);
        PointF bottomLeftPoint = calculatePoint(smallCircleCenterPoint, smallCircleRadius, segmentAngle + 90);
        PointF bottomRightPoint = calculatePoint(smallCircleCenterPoint, smallCircleRadius, segmentAngle - 90);

        // 先画大圆（如果需要）和小圆y
        if (hasBigCircle) {
            canvas.drawCircle(bigCircleCenterPoint.x, bigCircleCenterPoint.y, bigCircleRadius, mPaint);
        }
        canvas.drawCircle(smallCircleCenterPoint.x, smallCircleCenterPoint.y, smallCircleRadius, mPaint);

        // 再画梯形
        mPath.reset();
        mPath.moveTo(upperLeftPoint.x, upperLeftPoint.y);
        mPath.lineTo(upperRightPoint.x, upperRightPoint.y);
        mPath.lineTo(bottomRightPoint.x, bottomRightPoint.y);
        mPath.lineTo(bottomLeftPoint.x, bottomLeftPoint.y);
        // 因为 mPaint 的类型是 FILL，所以划线时不闭合也会自动将首尾相连
//        mPath.lineTo(upperLeftPoint.x,upperLeftPoint.y);
        canvas.drawPath(mPath, mPaint);

        return smallCircleCenterPoint;
    }

    /**
     * 鱼鳍其实用二阶贝塞尔曲线画出来的，鱼鳍长度是已知的，我们设置 FINS_LENGTH 为 1.3R，
     * 另外控制点与起点的距离，以及这二点连线与x轴的夹角，也是根据效果图测量后按比例给出的。
     */
    private void makeFin(Canvas canvas, PointF startPoint, float fishAngle, boolean isLeftFin) {
        // 鱼鳍的二阶贝塞尔曲线，控制点与起点连线长度是鱼鳍长度的1.8倍，夹角为115°
        float controlPointAngle = 110;
        // 计算鱼鳍终点坐标，起始点与结束点方向刚好与鱼头方向相反，因此要-180
        PointF endPoint = calculatePoint(startPoint, FINS_LENGTH, fishAngle - 180);
        // 鱼鳍不动时的控制点，以鱼头方向为准，左侧鱼鳍增加 controlPointAngle，右侧则减。
//        PointF controlPoint = calculatePoint(startPoint, FINS_LENGTH * 1.8f,
//                isLeftFin ? fishAngle + controlPointAngle : fishAngle - controlPointAngle);

        // 开始计算鱼鳍摆动时的控制点
        float controlFishCrossLength = (float) (FINS_LENGTH * 1.8f * Math.cos(Math.toRadians(70)));
        PointF controlFishCrossPoint = calculatePoint(startPoint, controlFishCrossLength, fishAngle - 180);
        // 最远的控制点到 controlFishCrossPoint 的距离，当然 controlFishCrossLength 也可以换成 HEAD_RADIUS
        float lineLength = (float) Math.abs(Math.tan(Math.toRadians(controlPointAngle)) * controlFishCrossLength);
        float line = lineLength - finsValue;
        PointF controlPoint = calculatePoint(controlFishCrossPoint, line,
                isLeftFin ? fishAngle + 90 : fishAngle - 90);

        // 开始绘制
        mPath.reset();
        mPath.moveTo(startPoint.x, startPoint.y);
        mPath.quadTo(controlPoint.x, controlPoint.y, endPoint.x, endPoint.y);
        canvas.drawPath(mPath, mPaint);
    }

    /**
     * 利用三角函数，通过两点形成的线长以及该线与x轴形成的夹角求出待求点坐标
     *
     * @param startPoint 起始点
     * @param length     待求点与起始点的直线距离
     * @param angle      两点连线与x轴夹角
     */
    public PointF calculatePoint(PointF startPoint, float length, float angle) {
        float deltaX = (float) (Math.cos(Math.toRadians(angle)) * length);
        // 由于数学中的坐标系Y轴向上，而 Android 屏幕中Y轴向下，因此通过数学方式求得的Y坐标与 Android
        // 屏幕的Y坐标是相反的。所以通过数学求出的Y轴坐标要取反，或者计算 sin 时给角度减去180换算成屏幕坐标。
        float deltaY = (float) (Math.sin(Math.toRadians(angle - 180)) * length);
        return new PointF(startPoint.x + deltaX, startPoint.y + deltaY);
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicHeight() {
        return (int) (SIZE_MULTIPLE_NUMBER * HEAD_RADIUS);
    }

    @Override
    public int getIntrinsicWidth() {
        return (int) (SIZE_MULTIPLE_NUMBER * HEAD_RADIUS);
    }

    public PointF getMiddlePoint() {
        return middlePoint;
    }

    public PointF getHeadPoint() {
        return headPoint;
    }

    public void setFrequency(float frequency) {
        this.frequency = frequency;
    }

    public void setFishMainAngle(float fishMainAngle) {
        this.fishMainAngle = fishMainAngle;
    }

    public float getFinsValue() {
        return finsValue;
    }

    public void setFinsValue(float finsValue) {
        this.finsValue = finsValue;
    }
}
