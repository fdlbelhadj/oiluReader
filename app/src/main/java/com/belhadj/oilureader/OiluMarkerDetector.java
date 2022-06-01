/*
some parts of this code are inspired and rewritten from
https://github.com/fnoop/aruco/blob/master/src/markerdetector.cpp
 */

/*

Copyright 2011 Rafael Muñoz Salinas. All rights reserved.
        Redistribution and use in source and binary forms, with or without modification, are
        permitted provided that the following conditions are met:
        1. Redistributions of source code must retain the above copyright notice, this list of
        conditions and the following disclaimer.
        2. Redistributions in binary form must reproduce the above copyright notice, this list
        of conditions and the following disclaimer in the documentation and/or other materials
        provided with the distribution.
        THIS SOFTWARE IS PROVIDED BY Rafael Muñoz Salinas ''AS IS'' AND ANY EXPRESS OR IMPLIED
        WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
        FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Rafael Muñoz Salinas OR
        CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
        CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
        SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
        ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
        NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
        ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
        The views and conclusions contained in the software and documentation are those of the
        authors and should not be interpreted as representing official policies, either expressed
        or implied, of Rafael Muñoz Salinas.
*/
package com.belhadj.oilureader;

import android.util.Log;
import android.util.TimingLogger;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class OiluMarkerDetector {

    DetectorParameters detectorParams;
    private  int minContourSizeAllowed;
    private Size canonicalMarkerSize;
    private  boolean debug;
    public Mat thresholdImage;
    private Mat greyImage;
    TimingLogger timingLogger;
    //debugWindow debugW;

    public OiluMarkerDetector(int m_minContourSizeAllowed, Size canonicalMarkerSize, boolean debug )
    {
        detectorParams = new DetectorParameters();

        minContourSizeAllowed = m_minContourSizeAllowed;
        this.canonicalMarkerSize = canonicalMarkerSize;
        this.debug = debug;
    }


    public List<OiluMarker> getCandidateMarkers(Mat _image)
    {
        if (_image.empty()) return null;

        greyImage = ConvertToGrey(_image);
        List<MatOfPoint> candidatesSet = DetectCandidates(greyImage);
        List<OiluMarker> list = getRefinedMarkerList(candidatesSet);

        return  list;
    }

    private List<OiluMarker> getRefinedMarkerList(List<MatOfPoint> candidates)
    {

        List<OiluMarker> markerList = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++)
        {
            Mat canonicalMarker = new Mat();
            Vector<Point> corners = new Vector<Point>();
            Converters.Mat_to_vector_Point2d(candidates.get(i), corners);

            OiluMarker marker = new OiluMarker(corners, canonicalMarkerSize);

            marker.correctPerspective(greyImage, canonicalMarker);
            marker.setDataMat(canonicalMarker);

            markerList.add(marker);

        }

        return markerList;
    }

    List<MatOfPoint> DetectCandidates(Mat greyImage)
    {

        Vector<MatOfPoint> candidates = new Vector<MatOfPoint>();
        Vector<MatOfPoint> contours = new Vector<MatOfPoint>();

        /// TODO: work on execution speed
        DetectInitialCandidates(greyImage, candidates, contours);

        candidates = _reorderCandidatesCorners(candidates);

        /// TODO: work on execution speed
        List<MatOfPoint> res = _filterTooCloseCandidates(candidates, contours);

        return res;

    }

//    private void ShowContours(VectorOfVectorOfPoint contours, VectorOfVectorOfPointF candidates)
//    {
//        if (debug)
//        {
//            Mat contoursMat = new Mat(greyImage.Size, greyImage.Depth, greyImage.NumberOfChannels);
//            contoursMat.SetTo(new MCvScalar(0, 0, 0));
//            Imgproc.Polylines(contoursMat, contours, true, new MCvScalar(255, 255, 255));
//            debugW.ShowMat(contoursMat, "All contours");
//
//            contoursMat.SetTo(new MCvScalar(0, 0, 0));
//            VectorOfVectorOfPoint vect = new VectorOfVectorOfPoint();
//            for (int i = 0; i < candidates.Size; i++)
//            {
//                vect.Push(new VectorOfPoint(Array.ConvertAll(candidates[i].ToArray(), Point.Round)));
//            }
//            Imgproc.Polylines(contoursMat, vect, true, new MCvScalar(255, 255, 255));
//            debugW.ShowMat(contoursMat, "squares approximés");
//        }
//    }

    public void DetectInitialCandidates(Mat grey, Vector<MatOfPoint> candidates, Vector<MatOfPoint> contours)
    {

        int nScales = (detectorParams.adaptiveThreshWinSizeMax - detectorParams.adaptiveThreshWinSizeMin) /
                detectorParams.adaptiveThreshWinSizeStep + 1;

        // ADD two cases of thresholding : canny and binarization threshold
        List<List<MatOfPoint>> candidatesArrays = new ArrayList<>(nScales + 2);
        for (int i = 0; i < nScales+2; i++)
            candidatesArrays.add(new ArrayList<MatOfPoint>());

        List<List<MatOfPoint>> contoursArrays = new ArrayList<>(nScales + 2);
        for (int i = 0; i < nScales+2; i++)
            contoursArrays.add( new ArrayList<MatOfPoint>());

        for (int range = 0; range < nScales; range++)
        //Parallel.For(0, nScales, range =>
        {
            int currScale = detectorParams.adaptiveThreshWinSizeMin + range * detectorParams.adaptiveThreshWinSizeStep;
            Mat thresh = new Mat();
            Threshold(grey, thresh, currScale, detectorParams.adaptiveThreshConstant);
            FindImageContours(thresh, candidatesArrays.get(range), contoursArrays.get(range));

        }
        //);



        // to avoid the adaptivethreshold artifacts, add a threshold binarization case
//        thresholdImage = new Mat();
//        Imgproc.threshold(grey, thresholdImage, detectorParams.binarizationThresh, 255, Imgproc.THRESH_BINARY);
//        FindImageContours(thresholdImage, candidatesArrays.get(nScales), contoursArrays.get(nScales));
        //if (debug) debugW.ShowMat(thresholdImage, "thresholding thresh");

//        Mat cannyImg = new Mat();
//        Imgproc.Canny(grey, cannyImg, detectorParams.cannyThreshold, detectorParams.cannyThresholdLinking);
//        FindImageContours(thresholdImage, candidatesArrays.get(nScales + 1), contoursArrays.get(nScales + 1));
        //if (debug) debugW.ShowMat(cannyImg, "cannyImg");

        // join candidates
        for (int i = 0; i < nScales + 2; i++)
        {
            for (int j = 0; j < candidatesArrays.get(i).size(); j++)
            {
                candidates.add(candidatesArrays.get(i).get(j));
                contours.add(contoursArrays.get(i).get(j));
            }
        }
    }

    public void FindImageContours(Mat _inThresh, List<MatOfPoint> candidatesOut, List<MatOfPoint> contoursOut)
    {
        //CVASSERT
        if (!(detectorParams.minMarkerPerimeterRate > 0 && detectorParams.maxMarkerPerimeterRate > 0 &&
                detectorParams.polygonalApproxAccuracyRate > 0 && detectorParams.minCornerDistanceRate >= 0 &&
                detectorParams.minDistanceToBorder >= 0))
            return;

        // calculate maximum and minimum sizes in pixels
        int minPerimeterPixels =
                (int)(detectorParams.minMarkerPerimeterRate * Math.max(_inThresh.cols(), _inThresh.rows()));
        int maxPerimeterPixels =
                (int)(detectorParams.maxMarkerPerimeterRate * Math.max(_inThresh.cols(), _inThresh.rows()));

        Mat contoursImg = new Mat();
        _inThresh.copyTo(contoursImg);
        List<MatOfPoint> contours = new ArrayList<>();
        Mat h = new Mat();
        Imgproc.findContours(contoursImg, contours, h, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_NONE);
        // now filter list of contours
        for (int i = 0; i < contours.size(); i++)
        {
            // check perimeter
            if (contours.get(i).total() < minPerimeterPixels || contours.get(i).total() > maxPerimeterPixels)
                continue;

            // check is square and is convex
            MatOfPoint2f c2f = new MatOfPoint2f(contours.get(i).toArray());
            double perim = Imgproc.arcLength(c2f, true);
            MatOfPoint2f approxCurve = new MatOfPoint2f();
            Imgproc.approxPolyDP(c2f, approxCurve, perim * detectorParams.polygonalApproxAccuracyRate, true);

            MatOfPoint approxC = new MatOfPoint();
            approxCurve.convertTo(approxC, CvType.CV_32SC2);

            if (approxCurve.total() != 4 || !Imgproc.isContourConvex(approxC)) continue;

            // check min distance between corners
            double minDistSq =
                    Math.max(contoursImg.cols(), contoursImg.rows()) * Math.max(contoursImg.cols(), contoursImg.rows());
            Point[] approxPoints = approxC.toArray();

            for (int j = 0; j < 4; j++)
            {
                double d = (double)(approxPoints[j].x - approxPoints[(j + 1) % 4].x) *
                        (double)(approxPoints[j].x - approxPoints[(j + 1) % 4].x) +
                        (double)(approxPoints[j].y - approxPoints[(j + 1) % 4].y) *
                                (double)(approxPoints[j].y - approxPoints[(j + 1) % 4].y);
                minDistSq = Math.min(minDistSq, d);
            }
            double minCornerDistancePixels = (double)(contours.get(i).total()) * detectorParams.minCornerDistanceRate;
            //TODO: check this formula
            minCornerDistancePixels = minContourSizeAllowed;
            if (minDistSq < minCornerDistancePixels * minCornerDistancePixels) continue;

            // check if it is too near to the image border
            boolean tooNearBorder = false;
            for (int j = 0; j < 4; j++)
            {
                if (approxPoints[j].x < detectorParams.minDistanceToBorder ||
                        approxPoints[j].y < detectorParams.minDistanceToBorder ||
                        approxPoints[j].x > contoursImg.cols() - 1 - detectorParams.minDistanceToBorder ||
                        approxPoints[j].y > contoursImg.rows() - 1 - detectorParams.minDistanceToBorder)
                    tooNearBorder = true;
            }
            if (tooNearBorder) continue;

            // if it passes all the test, add to candidates vector

            candidatesOut.add(approxC);
            contoursOut.add(contours.get(i));
        }
    }

    List<MatOfPoint> _filterTooCloseCandidates(Vector<MatOfPoint> candidatesIn, Vector<MatOfPoint> contoursIn)
    {

        if (!(detectorParams.minMarkerDistanceRate >= 0)) return null;

        //candGroup.resize(candidatesIn.size(), -1);
        int[] candGroup = new int[candidatesIn.size()];
        for (int i = 0; i < candidatesIn.size(); i++) candGroup[i] = -1;

        //vector < vector < unsigned int>> groupedCandidates;

        List<List<Integer>> groupedCandidates = new ArrayList<>();
        for (int i = 0; i < candidatesIn.size(); i++)
        {
            for (int j = i + 1; j < candidatesIn.size(); j++)
            {

                int minimumPerimeter = Math.min((int)contoursIn.get(i).total(), (int)contoursIn.get(j).total());

                // fc is the first corner considered on one of the markers, 4 combinations are possible
                Point[] ptsi = candidatesIn.get(i).toArray();
                Point[] ptsj = candidatesIn.get(j).toArray();
                for (int fc = 0; fc < 4; fc++)
                {
                    double distSq = 0;
                    for (int c = 0; c < 4; c++)
                    {
                        // modC is the corner considering first corner is fc
                        int modC = (c + fc) % 4;
                        distSq +=   (ptsi[modC].x - ptsj[c].x) * (ptsi[modC].x - ptsj[c].x) +
                                    (ptsi[modC].y - ptsj[c].y) * (ptsi[modC].y - ptsj[c].y);
                    }
                    distSq /= 4.0;

                    // if mean square distance is too low, remove the smaller one of the two markers
                    double minMarkerDistancePixels = (double)(minimumPerimeter) * detectorParams.minMarkerDistanceRate;
                    if (distSq < minMarkerDistancePixels * minMarkerDistancePixels)
                    {

                        // i and j are not related to a group
                        if (candGroup[i] < 0 && candGroup[j] < 0)
                        {
                            // mark candidates with their corresponding group number
                            candGroup[i] = candGroup[j] = groupedCandidates.size();

                            // create group
                            List<Integer> grouped = new ArrayList<>();
                            grouped.add(i);
                            grouped.add(j);
                            groupedCandidates.add(grouped);
                        }
                        // i is related to a group
                        else if (candGroup[i] > -1 && candGroup[j] == -1)
                        {
                            int group = candGroup[i];
                            candGroup[j] = group;

                            // add to group
                            groupedCandidates.get(group).add(j);
                        }
                        // j is related to a group
                        else if (candGroup[j] > -1 && candGroup[i] == -1)
                        {
                            int group = candGroup[j];
                            candGroup[i] = group;

                            // add to group
                            groupedCandidates.get(group).add(i);
                        }
                    }
                }
            }
        }

        // save possible candidates

        List<MatOfPoint> biggerCandidates = new ArrayList<>();
        //VectorOfVectorOfPoint biggerContours = new VectorOfVectorOfPoint();

        // save possible candidates
        for (int i = 0; i < groupedCandidates.size(); i++)
        {
            int smallerIdx = groupedCandidates.get(i).get(0);
            int biggerIdx = smallerIdx;
            double smallerArea = Imgproc.contourArea(candidatesIn.get(smallerIdx));
            double biggerArea = smallerArea;

            // evaluate group elements
            for (int j = 1; j < groupedCandidates.get(i).size(); j++)
            {
                int currIdx = groupedCandidates.get(i).get(j);
                double currArea = Imgproc.contourArea(candidatesIn.get(currIdx));

                // check if current contour is bigger
                if (currArea >= biggerArea)
                {
                    biggerIdx = currIdx;
                    biggerArea = currArea;
                }

                // check if current contour is smaller
                if (currArea < smallerArea && detectorParams.detectInvertedMarker)
                {
                    smallerIdx = currIdx;
                    smallerArea = currArea;
                }
            }

            // add contours and candidates
            biggerCandidates.add(candidatesIn.get(biggerIdx));
            //biggerContours.Push(contoursIn[biggerIdx]);
        }

        return biggerCandidates;
    }


    void Threshold(Mat _in, Mat _out, int winSize, double constant)
    {

        if (!(winSize >= 3)) return;
        if (winSize % 2 == 0) winSize++; // win size must be odd
        Imgproc.adaptiveThreshold(_in, _out, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, winSize, constant);
    }

    static Mat ConvertToGrey(Mat _in)
    {

        //if (_in.depth() == CvType.CV_8U && _in.channels() == 1)
                //|| (_in.channels() == 3)))
        //    return _in;

        Mat _out = new Mat();
        if (_in.channels() >= 3)
            Imgproc.cvtColor(_in, _out,  Imgproc.COLOR_BGR2GRAY);
        else
            return _in;

        return _out;
    }

    static Vector<MatOfPoint> _reorderCandidatesCorners(Vector<MatOfPoint> candidates)
    {
        int size = candidates.size();
        Vector<MatOfPoint>  refinedCandidates = new Vector<>(size);

        for (int i = 0; i < size; i++)
        {
            Point[] pointfs = candidates.get(i).toArray();
            
            double dx1 = pointfs[1].x - pointfs[0].x;
            double dy1 = pointfs[1].y - pointfs[0].y;
            double dx2 = pointfs[2].x - pointfs[0].x;
            double dy2 = pointfs[2].y - pointfs[0].y;
            double crossProduct = (dx1 * dy2) - (dy1 * dx2);

            if (crossProduct < 0.0)
            { // not clockwise direction
                //swap(candidates[i][1], candidates[i][3]);
                Point p = pointfs[1];
                pointfs[1] = pointfs[3];
                pointfs[3] = p;
            }
            refinedCandidates.add( new MatOfPoint(pointfs));

        }

        return refinedCandidates;
    }


}
