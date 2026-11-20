using System;
using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Animation;

namespace A7Battery
{
    /// <summary>
    /// UI 动效工具（参考 mochi-compose 的 iOS 风格弹簧与 MD3 Expressive）。
    /// 所有动画都轻量、可中断，并遵循「减少动态效果」系统设置。
    /// </summary>
    public static class UiAnimations
    {
        public static bool ReduceMotion => SystemParameters.ClientAreaAnimation == false;

        private static Storyboard ScaleBounce(ScaleTransform transform, double dip, int dipMs, int totalMs, IEasingFunction? rebound)
        {
            var sb = new Storyboard();

            var kf = new DoubleAnimationUsingKeyFrames
            {
                KeyFrames =
                {
                    new EasingDoubleKeyFrame(1, KeyTime.FromTimeSpan(TimeSpan.Zero)),
                    new EasingDoubleKeyFrame(dip, KeyTime.FromTimeSpan(TimeSpan.FromMilliseconds(dipMs)))
                    {
                        EasingFunction = new QuadraticEase { EasingMode = EasingMode.EaseOut }
                    },
                    new EasingDoubleKeyFrame(1, KeyTime.FromTimeSpan(TimeSpan.FromMilliseconds(totalMs)))
                    {
                        EasingFunction = rebound ?? new CubicEase { EasingMode = EasingMode.EaseOut }
                    }
                }
            };
            Storyboard.SetTarget(kf, transform);
            Storyboard.SetTargetProperty(kf, new PropertyPath(ScaleTransform.ScaleXProperty));
            sb.Children.Add(kf);

            var kfY = new DoubleAnimationUsingKeyFrames
            {
                KeyFrames =
                {
                    new EasingDoubleKeyFrame(1, KeyTime.FromTimeSpan(TimeSpan.Zero)),
                    new EasingDoubleKeyFrame(dip, KeyTime.FromTimeSpan(TimeSpan.FromMilliseconds(dipMs)))
                    {
                        EasingFunction = new QuadraticEase { EasingMode = EasingMode.EaseOut }
                    },
                    new EasingDoubleKeyFrame(1, KeyTime.FromTimeSpan(TimeSpan.FromMilliseconds(totalMs)))
                    {
                        EasingFunction = rebound ?? new CubicEase { EasingMode = EasingMode.EaseOut }
                    }
                }
            };
            Storyboard.SetTarget(kfY, transform);
            Storyboard.SetTargetProperty(kfY, new PropertyPath(ScaleTransform.ScaleYProperty));
            sb.Children.Add(kfY);
            return sb;
        }

        private static ScaleTransform EnsureScale(FrameworkElement el)
        {
            el.RenderTransformOrigin = new Point(0.5, 0.5);
            if (el.RenderTransform is ScaleTransform st) return st;
            st = new ScaleTransform(1, 1);
            el.RenderTransform = st;
            return st;
        }

        /// <summary>卡片依次淡入 + 轻微上移（stagger 入场）。</summary>
        public static void StaggerFadeIn(params FrameworkElement[] elements)
        {
            if (ReduceMotion || elements == null || elements.Length == 0) return;
            for (int i = 0; i < elements.Length; i++)
            {
                var el = elements[i];
                el.Opacity = 0;
                el.RenderTransform = new TranslateTransform(0, 14);

                var group = new Storyboard();
                var fade = new DoubleAnimation(0, 1, TimeSpan.FromMilliseconds(420))
                {
                    EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut }
                };
                Storyboard.SetTarget(fade, el);
                Storyboard.SetTargetProperty(fade, new PropertyPath(FrameworkElement.OpacityProperty));
                group.Children.Add(fade);

                var slide = new DoubleAnimation(14, 0, TimeSpan.FromMilliseconds(480))
                {
                    EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut }
                };
                Storyboard.SetTarget(slide, el);
                Storyboard.SetTargetProperty(slide, new PropertyPath("RenderTransform.(TranslateTransform.Y)"));
                group.Children.Add(slide);

                group.BeginTime = TimeSpan.FromMilliseconds(60 * i);
                group.Begin();
            }
        }

        /// <summary>按压回弹：缩放到 0.96 再用弹簧弹回（iOS 风格）。</summary>
        public static void PressBounce(FrameworkElement el, double scale = 0.96)
        {
            if (ReduceMotion || el == null) return;
            var st = EnsureScale(el);
            var sb = ScaleBounce(st, scale, 90, 360,
                new ElasticEase { EasingMode = EasingMode.EaseOut, Oscillations = 1, Springiness = 6 });
            sb.Begin();
        }

        /// <summary>柔和脉冲（数值变化 / 主题切换反馈）。</summary>
        public static void GentlePulse(FrameworkElement el)
        {
            if (ReduceMotion || el == null) return;
            var st = EnsureScale(el);
            var sb = ScaleBounce(st, 1.04, 140, 320,
                new CubicEase { EasingMode = EasingMode.EaseInOut });
            sb.Begin();
        }
    }
}
